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

import { uniqueName } from '@utils-commands/misc';
import { AutomationReporterDefOverrides, buildAutomationReporterDef, buildSystemAutomationReporterDef } from './automation-definitions';
import { AutomationDomainFixture, AutomationDomainFixtureOptions, setupAutomationDomainFixture } from './automation-domain-fixture';

/** Response shape returned by every reporter helper, so tests can assert status and body together. */
export interface ReporterPutResult {
  key: string;
  response: any;
}

export interface AutomationReporterFixture extends AutomationDomainFixture {
  /** PUT a reporter definition under a fresh key, tracked for cleanup. */
  putNewReporter(overrides?: AutomationReporterDefOverrides): Promise<ReporterPutResult>;

  /** PUT a minimal `{key, system:true}` definition under a fresh key, tracked for cleanup. */
  putNewSystemReporter(overrides?: AutomationReporterDefOverrides): Promise<ReporterPutResult>;

  /** Re-PUT an existing key — the update half of the idempotent create-or-update. */
  putReporter(key: string, overrides?: AutomationReporterDefOverrides): Promise<any>;

  /** Track a key created by a test that builds its own definition, so cleanup still covers it. */
  trackReporter(key: string): string;

  /** Deletes every tracked reporter; call from afterEach so tests stay independent. */
  cleanUpReporters(): Promise<void>;
}

export const setupAutomationReporterFixture = async (
  options: AutomationDomainFixtureOptions = { keyPrefix: 'autorep' },
): Promise<AutomationReporterFixture> => {
  const base = await setupAutomationDomainFixture(options);
  const createdReporterKeys: string[] = [];

  const trackReporter = (key: string): string => {
    createdReporterKeys.push(key);
    return key;
  };

  const putReporter = (key: string, overrides: AutomationReporterDefOverrides = {}) =>
    base.client.putReporter(base.domainKey, buildAutomationReporterDef({ ...overrides, key }));

  const putNewReporter = async (overrides: AutomationReporterDefOverrides = {}): Promise<ReporterPutResult> => {
    const key = trackReporter(overrides.key ?? uniqueName('autoaudit', true).toLowerCase());
    return { key, response: await putReporter(key, overrides) };
  };

  const putNewSystemReporter = async (overrides: AutomationReporterDefOverrides = {}): Promise<ReporterPutResult> => {
    const key = trackReporter(overrides.key ?? uniqueName('autosysrep', true).toLowerCase());
    const response = await base.client.putReporter(base.domainKey, buildSystemAutomationReporterDef({ ...overrides, key }));
    return { key, response };
  };

  const cleanUpReporters = async (): Promise<void> => {
    while (createdReporterKeys.length) {
      // tolerant: a test may have already deleted its reporter (404), which is fine
      await base.client.deleteReporter(base.domainKey, createdReporterKeys.pop());
    }
  };

  return {
    ...base,
    putNewReporter,
    putNewSystemReporter,
    putReporter,
    trackReporter,
    cleanUpReporters,
    cleanUp: async () => {
      await cleanUpReporters();
      await base.cleanUp();
    },
  };
};
