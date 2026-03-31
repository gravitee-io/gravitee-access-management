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

import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import type { Email } from '@management-models/Email';
import type { Form } from '@management-models/Form';

/** Distribution template; used when the domain has no persisted RESET_PASSWORD email row yet. */
const RESET_PASSWORD_EMAIL_TEMPLATE_PATH = join(
  __dirname,
  '../../../gravitee-am-gateway/gravitee-am-gateway-standalone/gravitee-am-gateway-standalone-distribution/src/main/resources/templates/reset_password.html',
);

/**
 * GET a domain sub-resource under Management API (generated SDK marks some find* methods void).
 * @param domainId domain UUID
 * @param relativePath e.g. `forms?template=LOGIN` or `emails?template=RESET_PASSWORD`
 */
export async function fetchManagementDomainResource<T>(domainId: string, accessToken: string, relativePath: string): Promise<T> {
  const org = process.env.AM_DEF_ORG_ID;
  const env = process.env.AM_DEF_ENV_ID;
  const base = (process.env.AM_MANAGEMENT_ENDPOINT || '').replace(/\/$/, '');
  if (!org || !env || !base) {
    throw new Error('AM_DEF_ORG_ID, AM_DEF_ENV_ID, and AM_MANAGEMENT_ENDPOINT must be set');
  }
  const url = `${base}/organizations/${encodeURIComponent(org)}/environments/${encodeURIComponent(env)}/domains/${encodeURIComponent(domainId)}/${relativePath}`;
  const res = await globalThis.fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Management GET ${url} failed: ${res.status} ${text}`);
  }
  return (await res.json()) as T;
}

export async function fetchDomainLoginForm(domainId: string, accessToken: string): Promise<Form> {
  return fetchManagementDomainResource<Form>(domainId, accessToken, 'forms?template=LOGIN');
}

export async function fetchDomainResetPasswordEmail(domainId: string, accessToken: string): Promise<Email> {
  return fetchManagementDomainResource<Email>(domainId, accessToken, 'emails?template=RESET_PASSWORD');
}

/**
 * Persist a RESET_PASSWORD email template when GET returns the in-memory default (no {@link Email.id}).
 * POST returns 201 with the created {@link Email} body.
 */
export async function createPersistedResetPasswordEmail(domainId: string, accessToken: string): Promise<Email> {
  const org = process.env.AM_DEF_ORG_ID;
  const env = process.env.AM_DEF_ENV_ID;
  const base = (process.env.AM_MANAGEMENT_ENDPOINT || '').replace(/\/$/, '');
  if (!org || !env || !base) {
    throw new Error('AM_DEF_ORG_ID, AM_DEF_ENV_ID, and AM_MANAGEMENT_ENDPOINT must be set');
  }
  const content = readFileSync(RESET_PASSWORD_EMAIL_TEMPLATE_PATH, 'utf-8');
  const url = `${base}/organizations/${encodeURIComponent(org)}/environments/${encodeURIComponent(env)}/domains/${encodeURIComponent(domainId)}/emails`;
  const res = await globalThis.fetch(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      template: 'RESET_PASSWORD',
      enabled: true,
      from: 'noreply@gravitee.io',
      fromName: 'Gravitee',
      subject: 'Reset your password',
      content,
      expiresAfter: 3600,
    }),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Management POST ${url} failed: ${res.status} ${text}`);
  }
  return (await res.json()) as Email;
}

export async function fetchOrCreateDomainResetPasswordEmail(domainId: string, accessToken: string): Promise<Email> {
  const existing = await fetchDomainResetPasswordEmail(domainId, accessToken);
  if (existing.id) {
    return existing;
  }
  return createPersistedResetPasswordEmail(domainId, accessToken);
}
