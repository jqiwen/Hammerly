#!/usr/bin/env bash
set -euo pipefail

KAFKA_USER="kafka"
KAFKA_GROUP="kafka"
KAFKA_HOME="/opt/kafka"
KAFKA_DATA_DIR="/var/lib/kafka/data"
KAFKA_CONFIG_DIR="/etc/kafka"
KAFKA_CONFIG_FILE="${KAFKA_CONFIG_DIR}/server.properties"
KAFKA_VERSION="$(curl --fail --silent --show-error \
  -H 'Metadata-Flavor: Google' \
  'http://metadata.google.internal/computeMetadata/v1/instance/attributes/hammerly-kafka-version')"
PRIVATE_IP="$(curl --fail --silent --show-error \
  -H 'Metadata-Flavor: Google' \
  'http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/ip')"

if [[ ! -x "${KAFKA_HOME}/bin/kafka-server-start.sh" ]]; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install --yes --no-install-recommends ca-certificates curl openjdk-21-jre-headless

  archive="/tmp/kafka_${KAFKA_VERSION}.tgz"
  checksum="${archive}.sha512"
  base_url="https://archive.apache.org/dist/kafka/${KAFKA_VERSION}/kafka_2.13-${KAFKA_VERSION}.tgz"
  curl --fail --location --retry 5 --retry-delay 3 --output "${archive}" "${base_url}"
  curl --fail --location --retry 5 --retry-delay 3 --output "${checksum}" "${base_url}.sha512"
  expected="$(sed 's/^[^:]*://' "${checksum}" | tr -d '\r\n ' | tr '[:upper:]' '[:lower:]')"
  actual="$(sha512sum "${archive}" | awk '{print $1}')"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "Kafka archive checksum verification failed" >&2
    exit 1
  fi

  install -d -m 0755 "${KAFKA_HOME}"
  tar --extract --gzip --file "${archive}" --strip-components=1 --directory "${KAFKA_HOME}"
  rm -f "${archive}" "${checksum}"
fi

if ! getent group "${KAFKA_GROUP}" >/dev/null; then
  groupadd --system "${KAFKA_GROUP}"
fi
if ! id "${KAFKA_USER}" >/dev/null 2>&1; then
  useradd --system --gid "${KAFKA_GROUP}" --home-dir "${KAFKA_DATA_DIR}" \
    --shell /usr/sbin/nologin "${KAFKA_USER}"
fi

install -d -o "${KAFKA_USER}" -g "${KAFKA_GROUP}" -m 0750 \
  "${KAFKA_DATA_DIR}" "${KAFKA_CONFIG_DIR}"

cat > "${KAFKA_CONFIG_FILE}" <<EOF
process.roles=broker,controller
node.id=1
controller.quorum.bootstrap.servers=127.0.0.1:9093
controller.listener.names=CONTROLLER
listeners=PLAINTEXT://0.0.0.0:9092,CONTROLLER://127.0.0.1:9093
advertised.listeners=PLAINTEXT://${PRIVATE_IP}:9092
listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
inter.broker.listener.name=PLAINTEXT
log.dirs=${KAFKA_DATA_DIR}
num.partitions=3
default.replication.factor=1
min.insync.replicas=1
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
group.initial.rebalance.delay.ms=0
auto.create.topics.enable=false
delete.topic.enable=true
log.retention.hours=168
log.segment.bytes=268435456
EOF
chown "${KAFKA_USER}:${KAFKA_GROUP}" "${KAFKA_CONFIG_FILE}"
chmod 0640 "${KAFKA_CONFIG_FILE}"

if [[ ! -f "${KAFKA_DATA_DIR}/meta.properties" ]]; then
  cluster_id="$("${KAFKA_HOME}/bin/kafka-storage.sh" random-uuid)"
  "${KAFKA_HOME}/bin/kafka-storage.sh" format \
    --cluster-id "${cluster_id}" \
    --standalone \
    --config "${KAFKA_CONFIG_FILE}"
  chown -R "${KAFKA_USER}:${KAFKA_GROUP}" "${KAFKA_DATA_DIR}"
fi

cat > /etc/systemd/system/kafka.service <<EOF
[Unit]
Description=Hammerly single-node Apache Kafka (KRaft)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${KAFKA_USER}
Group=${KAFKA_GROUP}
Environment="KAFKA_HEAP_OPTS=-Xms512m -Xmx768m"
Environment="KAFKA_JVM_PERFORMANCE_OPTS=-server -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+ExplicitGCInvokesConcurrent"
ExecStart=${KAFKA_HOME}/bin/kafka-server-start.sh ${KAFKA_CONFIG_FILE}
ExecStop=${KAFKA_HOME}/bin/kafka-server-stop.sh
Restart=on-failure
RestartSec=5
LimitNOFILE=100000
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable kafka.service
systemctl restart kafka.service

for attempt in $(seq 1 60); do
  if "${KAFKA_HOME}/bin/kafka-broker-api-versions.sh" \
      --bootstrap-server 127.0.0.1:9092 >/dev/null 2>&1; then
    exit 0
  fi
  sleep 5
done

journalctl --unit kafka.service --no-pager --lines 100 >&2
exit 1
