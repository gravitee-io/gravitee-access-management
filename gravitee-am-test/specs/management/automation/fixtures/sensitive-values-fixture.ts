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
import { retryUntil } from '@utils-commands/retry';
import { createDomain, patchDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { getDomainApi } from '@management-commands/service/utils';
import { Audit } from '@management-models/Audit';
import { AutomationDomainFixture, setupAutomationDomainFixture } from './automation-domain-fixture';
import { buildAutomationReporterDef, buildInlineIdpDef } from './automation-definitions';
import { buildKafkaReporterConfig } from '../../reporters/fixtures/kafka-reporter-config-helper';

export const SENSITIVE_VALUES_TEST = {
  MASK: '********',
  PASSWORD: 'Sensitive-P@ssw0rd',
} as const;

export const newKey = (prefix: string) => uniqueName(prefix, true).toLowerCase();

export const buildSecretInlineIdpDef = (key: string, password: string = SENSITIVE_VALUES_TEST.PASSWORD) =>
  buildInlineIdpDef({ key, users: [{ username: 'alice', password }] });

export const buildSecretKafkaReporterDef = (key: string, password: string) =>
  buildAutomationReporterDef({
    key,
    configuration: JSON.stringify({ ...buildKafkaReporterConfig(), username: 'am', password }),
  });

export const configurationOf = (body: { configuration: string }) => JSON.parse(body.configuration);

/** A domain created through the Management API. */
export interface AuditedDomain {
  /** Addresses the domain through the Automation API. */
  domainRef: string;
  /** Resolves with the domain's audits of the given types, once each type has been recorded since `from`. */
  waitForAudits: (from: number, types: string[]) => Promise<Audit[]>;
}

export interface SensitiveValuesFixture extends AutomationDomainFixture {
  /** PUT under the fixture's domain, tracked for cleanup. */
  putIdentity: (definition: object) => Promise<any>;
  /** PUT under the fixture's domain, tracked for cleanup. */
  putCertificate: (definition: object) => Promise<any>;
  /** PUT under the fixture's domain, tracked for cleanup. */
  putReporter: (definition: object) => Promise<any>;
  /** Resolves once the domain's audit reporter records events. */
  createAuditedDomain: () => Promise<AuditedDomain>;
  /** Deletes every resource created through the fixture. */
  cleanUpResources: () => Promise<void>;
}

const keyOf = (definition: object) => (definition as { key: string }).key;

export const setupSensitiveValuesFixture = async (): Promise<SensitiveValuesFixture> => {
  const base = await setupAutomationDomainFixture({ keyPrefix: 'autosecret' });
  const { accessToken, client, domainKey } = base;
  const cleanUps: Array<() => Promise<unknown>> = [];

  const track = <T>(cleanUp: () => Promise<unknown>, action: () => Promise<T>): Promise<T> => {
    cleanUps.push(cleanUp);
    return action();
  };

  const listAudits = async (domainId: string, from: number): Promise<Audit[]> => {
    // the generated client types this response as an array, but the endpoint returns a page
    const response = await getDomainApi(accessToken).listDomainAuditsRaw({
      organizationId: process.env.AM_DEF_ORG_ID,
      environmentId: process.env.AM_DEF_ENV_ID,
      domain: domainId,
      from,
      size: 50,
    });
    const page: { data?: Audit[] } = await response.raw.json();
    return page.data ?? [];
  };

  const createAuditedDomain = async (): Promise<AuditedDomain> => {
    const domain = await createDomain(accessToken, uniqueName('autosecret-audit', true), 'Audited through the Automation API');
    cleanUps.push(() => safeDeleteDomain(domain.id, accessToken));

    // the reporter starts asynchronously, drops the events emitted before it is up, and flushes in batches
    const warmUpFrom = Date.now();
    await retryUntil(
      async () => {
        await patchDomain(domain.id, accessToken, { description: `Audit reporter probe ${Date.now()}` });
        return listAudits(domain.id, warmUpFrom);
      },
      (audits) => audits.some((audit) => audit.type === 'DOMAIN_UPDATED'),
      { timeoutMillis: 30_000, intervalMillis: 1_000 },
    );

    return {
      domainRef: `id:${domain.id}`,
      waitForAudits: async (from, types) => {
        const audits = await retryUntil(
          () => listAudits(domain.id, from),
          (recorded) => types.every((type) => recorded.some((audit) => audit.type === type)),
          { timeoutMillis: 30_000, intervalMillis: 1_000 },
        );
        return audits.filter((audit) => types.includes(audit.type));
      },
    };
  };

  const cleanUpResources = async () => {
    while (cleanUps.length) {
      await cleanUps.pop()();
    }
  };

  return {
    ...base,
    putIdentity: (definition) =>
      track(
        () => client.deleteIdentity(domainKey, keyOf(definition)),
        () => client.putIdentity(domainKey, definition),
      ),
    putCertificate: (definition) =>
      track(
        () => client.deleteCertificate(domainKey, keyOf(definition)),
        () => client.putCertificate(domainKey, definition),
      ),
    putReporter: (definition) =>
      track(
        () => client.deleteReporter(domainKey, keyOf(definition)),
        () => client.putReporter(domainKey, definition),
      ),
    createAuditedDomain,
    cleanUpResources,
    cleanUp: async () => {
      await cleanUpResources();
      await base.cleanUp();
    },
  };
};
