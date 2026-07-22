/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
const fs = require('fs');
const path = require('path');

/*
 * Opt-in test-harness observability. When ENABLE_METRICS_TRACKING is set, sample the AM node
 * monitor (JVM heap/GC/threads/file-descriptors) and domain-sync state for the gateway and the
 * management API at the start and end of every test suite, appended as NDJSON under jest-reports
 * (stored as a CI artifact). Off by default; a sampling failure never fails a test.
 */
if (process.env.ENABLE_METRICS_TRACKING) {
  const gwNode = process.env.AM_GATEWAY_NODE_MONITORING_URL || 'http://localhost:18092/_node';
  const mgmtNode = process.env.AM_MANAGEMENT_NODE_MONITORING_URL || 'http://localhost:18093/_node';
  const auth =
    'Basic ' +
    Buffer.from(`${process.env.AM_ADMIN_USERNAME || 'admin'}:${process.env.AM_ADMIN_PASSWORD || 'adminadmin'}`).toString('base64');
  const outFile =
    process.env.METRICS_OUTPUT_FILE || path.resolve(__dirname, '../../jest-reports/metrics', `metrics-${process.pid}.ndjson`);

  fs.mkdirSync(path.dirname(outFile), { recursive: true });

  const getJson = async (url) => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 4000);
    try {
      const res = await fetch(url, { headers: { Authorization: auth }, signal: controller.signal });
      return res.ok ? await res.json() : null;
    } catch {
      return null;
    } finally {
      clearTimeout(timeout);
    }
  };

  const monitor = (m) => {
    if (!m) return null;
    const heapUsed = m.jvm?.mem?.heapUsed ?? 0;
    const heapMax = m.jvm?.mem?.heapMax ?? 0;
    const gc = {};
    for (const c of m.jvm?.gc?.collectors ?? []) gc[c.name] = [c.collectionCount, c.collectionTime];
    return {
      heapUsedMb: Math.round(heapUsed / 1048576),
      heapMaxMb: Math.round(heapMax / 1048576),
      heapPct: heapMax ? Math.round((heapUsed / heapMax) * 100) : null,
      gc,
      threads: m.jvm?.threads?.count ?? null,
      fds: m.process?.openFileDescriptors ?? null,
      procCpu: m.process?.cpu?.percent ?? null,
      load: m.os?.cpu?.loadAverage?.[0] ?? null,
      osMemPct: m.os?.mem?.usedPercent ?? null,
    };
  };

  const domains = (d) => {
    if (!d || typeof d !== 'object') return null;
    const states = Object.values(d);
    return {
      count: states.length,
      notStable: states.filter((s) => s && !s.stable).length,
      notSynced: states.filter((s) => s && !s.synchronized).length,
    };
  };

  const capture = async (phase) => {
    const row = { ts: new Date().toISOString(), phase };
    try {
      row.suite = (expect.getState().testPath || 'unknown').replace(/^.*\/specs\//, 'specs/');
      const [gwMon, gwDomains, mgmtMon] = await Promise.all([
        getJson(`${gwNode}/monitor`),
        getJson(`${gwNode}/domains?output=json`),
        getJson(`${mgmtNode}/monitor`),
      ]);
      row.gw = monitor(gwMon);
      row.mgmt = monitor(mgmtMon);
      row.sync = domains(gwDomains);
    } catch (err) {
      row.error = String(err);
    }
    try {
      fs.appendFileSync(outFile, JSON.stringify(row) + '\n');
    } catch {
      /* never let metrics break a test */
    }
  };

  beforeAll(() => capture('start'));
  afterAll(() => capture('end'));
}
