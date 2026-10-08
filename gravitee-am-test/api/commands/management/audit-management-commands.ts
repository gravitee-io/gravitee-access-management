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
import { getAuditApi } from '@management-commands/service/utils';
import { retryUntil } from '@utils-commands/retry';

/** An audit entry as the Management API returns it. */
export interface AuditEntry {
  id?: string;
  type?: string;
  outcome?: { status?: string; message?: string };
  [key: string]: any;
}

export const listDomainAudits = (domainId: string, accessToken: string, type?: string, size = 200): Promise<any> =>
  getAuditApi(accessToken).listDomainAudits({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    type: type,
    size: size,
  });

const entriesOf = (page: any): AuditEntry[] => page?.data ?? page?.items ?? (Array.isArray(page) ? page : []);

/** Every audit of the given type, newest first as the API returns them. */
export async function auditsOfType(domainId: string, accessToken: string, type: string): Promise<AuditEntry[]> {
  return entriesOf(await listDomainAudits(domainId, accessToken, type)).filter((a) => a.type === type);
}

/** Ids of every audit of the given type, to use as a baseline before an action. */
export async function auditIdsOfType(domainId: string, accessToken: string, type: string): Promise<Set<string>> {
  return new Set((await auditsOfType(domainId, accessToken, type)).map((a) => a.id));
}

/**
 * Waits for an audit of the given type and status that is not in `knownIds`, and returns it.
 *
 * The baseline matters. Audits accumulate on a domain, so matching on type and status alone
 * returns an earlier test's audit and the assertion then passes for the wrong reason. Audits are
 * also written asynchronously, so this polls rather than reading once.
 */
export async function waitForNewAudit(
  domainId: string,
  accessToken: string,
  type: string,
  status: 'SUCCESS' | 'FAILURE',
  knownIds: Set<string>,
  // The reporter writes audits asynchronously and lags when suites run in parallel.
  timeoutMillis = 30000,
): Promise<AuditEntry> {
  const wanted = status.toLowerCase();
  return retryUntil(
    async () =>
      (await auditsOfType(domainId, accessToken, type)).find(
        (a) => !knownIds.has(a.id) && (a.outcome?.status ?? '').toLowerCase() === wanted,
      ),
    (audit) => !!audit,
    { timeoutMillis, intervalMillis: 500 },
  ).catch(() => {
    throw new Error(`No new ${type} audit with status ${status} within ${timeoutMillis}ms`);
  });
}

/** Number of audits of the given type currently recorded on the domain. */
export async function countAuditsOfType(domainId: string, accessToken: string, type: string): Promise<number> {
  return (await auditsOfType(domainId, accessToken, type)).length;
}
