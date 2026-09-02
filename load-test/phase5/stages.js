export function envDuration(name, fallback) {
  return __ENV[name] || fallback;
}

export function parseDuration(value) {
  const match = /^(\d+(?:\.\d+)?)(ms|s|m)$/.exec(value);
  if (!match) throw new Error(`Unsupported Phase 5 duration: ${value}`);
  const amount = Number(match[1]);
  return amount * ({ ms: 1, s: 1000, m: 60000 })[match[2]];
}

export function concurrencyAt(elapsedMs) {
  if ((__ENV.HAMMERLY_LOAD_TEST_MODE || 'smoke').toLowerCase() !== 'full') return 'smoke';
  const warmup = parseDuration(envDuration('PHASE5_WARMUP_DURATION', '30s'));
  const ramp = parseDuration(envDuration('PHASE5_RAMP_DURATION', '15s'));
  const stage100 = parseDuration(envDuration('PHASE5_100_DURATION', '2m'));
  const stage500 = parseDuration(envDuration('PHASE5_500_DURATION', '2m'));
  const stage1000 = parseDuration(envDuration('PHASE5_1000_DURATION', '2m'));
  let cursor = warmup + ramp;
  if (elapsedMs >= cursor && elapsedMs < cursor + stage100) return '100';
  cursor += stage100 + ramp;
  if (elapsedMs >= cursor && elapsedMs < cursor + stage500) return '500';
  cursor += stage500 + ramp;
  if (elapsedMs >= cursor && elapsedMs < cursor + stage1000) return '1000';
  return 'transition';
}

export function smokeStages() {
  return [
    { duration: envDuration('SMOKE_RAMP_DURATION', '5s'), target: 15 },
    { duration: envDuration('SMOKE_HOLD_DURATION', '20s'), target: 15 },
    { duration: envDuration('SMOKE_COOLDOWN_DURATION', '5s'), target: 0 },
  ];
}

export function fullStages() {
  return [
    { duration: envDuration('PHASE5_WARMUP_DURATION', '30s'), target: 25 },
    { duration: envDuration('PHASE5_RAMP_DURATION', '15s'), target: 100 },
    { duration: envDuration('PHASE5_100_DURATION', '2m'), target: 100 },
    { duration: envDuration('PHASE5_RAMP_DURATION', '15s'), target: 500 },
    { duration: envDuration('PHASE5_500_DURATION', '2m'), target: 500 },
    { duration: envDuration('PHASE5_RAMP_DURATION', '15s'), target: 1000 },
    { duration: envDuration('PHASE5_1000_DURATION', '2m'), target: 1000 },
    { duration: envDuration('PHASE5_COOLDOWN_DURATION', '30s'), target: 0 },
  ];
}
