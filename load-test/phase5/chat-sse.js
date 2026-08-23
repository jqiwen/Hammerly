import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import sse from 'k6/x/sse';
import { assertLoadTestSafety, endpoint, mode, requestHeaders } from './config.js';
import { concurrencyAt, fullStages, parseDuration, smokeStages } from './stages.js';

const connectionLatency = new Trend('hammerly_sse_connection_latency', true);
const firstEventLatency = new Trend('hammerly_sse_first_event_latency', true);
const streamDuration = new Trend('hammerly_sse_stream_duration', true);
const successfulStreams = new Counter('hammerly_sse_streams_successful');
const failedStreams = new Counter('hammerly_sse_streams_failed');
const streamFailureRate = new Rate('hammerly_sse_stream_failure_rate');
const overloadEvents = new Counter('hammerly_sse_controlled_overload');
const totalStreams = new Counter('hammerly_sse_streams_total');
const transportErrors = new Counter('hammerly_sse_transport_errors');
const errorEvents = new Counter('hammerly_sse_error_events');
const httpStatusErrors = new Counter('hammerly_sse_http_status_errors');
const missingFirstEvents = new Counter('hammerly_sse_missing_first_event');
const incompleteStreams = new Counter('hammerly_sse_incomplete_streams');

assertLoadTestSafety();

export const options = {
  scenarios: {
    chat_sse: {
      executor: 'ramping-vus',
      gracefulRampDown: '30s',
      gracefulStop: '30s',
      stages: mode === 'full' ? fullStages() : smokeStages(),
    },
  },
  thresholds: {
    hammerly_sse_stream_failure_rate: ['rate<0.20'],
    hammerly_sse_first_event_latency: ['p(95)<5000'],
    ...(mode === 'full' ? {
    'hammerly_sse_stream_failure_rate{concurrency:100}': ['rate<1.0'],
    'hammerly_sse_stream_failure_rate{concurrency:500}': ['rate<1.0'],
    'hammerly_sse_stream_failure_rate{concurrency:1000}': ['rate<1.0'],
    'hammerly_sse_connection_latency{concurrency:100}': ['p(95)<60000'],
    'hammerly_sse_connection_latency{concurrency:500}': ['p(95)<60000'],
    'hammerly_sse_connection_latency{concurrency:1000}': ['p(95)<60000'],
    'hammerly_sse_first_event_latency{concurrency:100}': ['p(95)<60000'],
    'hammerly_sse_first_event_latency{concurrency:500}': ['p(95)<60000'],
    'hammerly_sse_first_event_latency{concurrency:1000}': ['p(95)<60000'],
    'hammerly_sse_stream_duration{concurrency:100}': ['p(95)<60000'],
    'hammerly_sse_stream_duration{concurrency:500}': ['p(95)<60000'],
    'hammerly_sse_stream_duration{concurrency:1000}': ['p(95)<60000'],
    'hammerly_sse_stream_duration{concurrency:100,outcome:success}': ['p(95)<60000'],
    'hammerly_sse_stream_duration{concurrency:500,outcome:success}': ['p(95)<60000'],
    'hammerly_sse_stream_duration{concurrency:1000,outcome:success}': ['p(95)<60000'],
    'hammerly_sse_streams_total{concurrency:100}': ['count>0'],
    'hammerly_sse_streams_total{concurrency:500}': ['count>0'],
    'hammerly_sse_streams_total{concurrency:1000}': ['count>0'],
    'hammerly_sse_streams_successful{concurrency:100}': ['count>0'],
    'hammerly_sse_streams_successful{concurrency:500}': ['count>0'],
    'hammerly_sse_streams_successful{concurrency:1000}': ['count>0'],
    'hammerly_sse_error_events{concurrency:100}': ['count>=0'],
    'hammerly_sse_error_events{concurrency:500}': ['count>=0'],
    'hammerly_sse_error_events{concurrency:1000}': ['count>=0'],
    'hammerly_sse_transport_errors{concurrency:100}': ['count>=0'],
    'hammerly_sse_transport_errors{concurrency:500}': ['count>=0'],
    'hammerly_sse_transport_errors{concurrency:1000}': ['count>=0'],
    'hammerly_sse_http_status_errors{concurrency:100}': ['count>=0'],
    'hammerly_sse_http_status_errors{concurrency:500}': ['count>=0'],
    'hammerly_sse_http_status_errors{concurrency:1000}': ['count>=0'],
    'hammerly_sse_http_status_errors{status:429}': ['count>=0'],
    'hammerly_sse_http_status_errors{status:500}': ['count>=0'],
    'hammerly_sse_http_status_errors{status:503}': ['count>=0'],
    'hammerly_sse_http_status_errors{status:0}': ['count>=0'],
    'hammerly_sse_http_status_errors{status:undefined}': ['count>=0'],
    'hammerly_sse_http_status_errors{status:missing}': ['count>=0'],
    'hammerly_sse_missing_first_event{concurrency:100}': ['count>=0'],
    'hammerly_sse_missing_first_event{concurrency:500}': ['count>=0'],
    'hammerly_sse_missing_first_event{concurrency:1000}': ['count>=0'],
    'hammerly_sse_incomplete_streams{concurrency:100}': ['count>=0'],
    'hammerly_sse_incomplete_streams{concurrency:500}': ['count>=0'],
    'hammerly_sse_incomplete_streams{concurrency:1000}': ['count>=0'],
    } : {}),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function conversationId() {
  const vu = exec.vu.idInTest.toString(16).padStart(8, '0').slice(-8);
  const runShard = (__ENV.PHASE5_RUN_ID || '0000')
    .toLowerCase()
    .replace(/[^0-9a-f]/g, '')
    .padEnd(4, '0')
    .slice(0, 4);
  const iteration = exec.scenario.iterationInTest.toString(16).padStart(12, '0').slice(-12);
  return `${vu}-${runShard}-4000-8000-${iteration}`;
}

export default function () {
  const startedAt = Date.now();
  const metricTags = { concurrency: concurrencyAt(exec.instance.currentTestRunDuration) };
  let openedAt = 0;
  let firstChunkAt = 0;
  let completed = false;
  let failed = false;
  const body = JSON.stringify({
    message: 'How do I place a safe bid on Hammerly?',
    conversationId: conversationId(),
    history: [],
  });

  const response = sse.open(endpoint, {
    method: 'POST',
    headers: requestHeaders(),
    body,
    tags: { endpoint: 'ai_chat_sse', phase5_mode: mode },
  }, (client) => {
    client.on('open', () => {
      openedAt = Date.now();
      connectionLatency.add(openedAt - startedAt, metricTags);
    });
    client.on('event', (event) => {
      if (event.name === 'chunk' && firstChunkAt === 0) {
        firstChunkAt = Date.now();
        firstEventLatency.add(firstChunkAt - startedAt, metricTags);
      } else if (event.name === 'done') {
        completed = true;
        client.close();
      } else if (event.name === 'error') {
        failed = true;
        errorEvents.add(1, metricTags);
        if (event.data && event.data.includes('temporarily busy')) {
          overloadEvents.add(1, metricTags);
        }
        client.close();
      }
    });
    client.on('error', () => {
      failed = true;
      transportErrors.add(1, metricTags);
      client.close();
    });
  });

  const elapsed = Date.now() - startedAt;
  if (!response || response.status !== 200) {
    httpStatusErrors.add(1, {
      ...metricTags,
      status: response ? String(response.status) : 'missing',
    });
  }
  if (firstChunkAt === 0) {
    missingFirstEvents.add(1, metricTags);
  }
  if (!completed) {
    incompleteStreams.add(1, metricTags);
  }
  const ok = check(response, {
    'SSE status is 200': (result) => result && result.status === 200,
    'stream emitted first chunk': () => firstChunkAt > 0,
    'stream completed': () => completed && !failed,
  });
  const success = ok && completed && firstChunkAt > 0 && !failed;
  streamDuration.add(elapsed, { ...metricTags, outcome: success ? 'success' : 'failure' });
  streamFailureRate.add(!success, metricTags);
  totalStreams.add(1, metricTags);
  if (success) {
    successfulStreams.add(1, metricTags);
  } else {
    failedStreams.add(1, metricTags);
  }
  const thinkTimeMs = parseDuration(__ENV.PHASE5_THINK_TIME || '10s');
  const thinkTimeWithJitter = thinkTimeMs * (0.75 + Math.random() * 0.5);
  sleep(thinkTimeWithJitter / 1000);
}

export function handleSummary(data) {
  const destination = __ENV.PHASE5_SUMMARY_EXPORT || 'phase5-summary.json';
  return {
    [destination]: JSON.stringify(data, null, 2),
  };
}
