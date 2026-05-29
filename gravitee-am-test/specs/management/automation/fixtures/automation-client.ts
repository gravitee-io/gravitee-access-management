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
import { performDelete, performGet, performPut } from '@gateway-commands/oauth-oidc-commands';

export const automationUrl = (): string => `${process.env.AM_MANAGEMENT_URL}/management/automation`;

export const envPath = (): string =>
  `/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}`;

export const jsonHeaders = (extra: Record<string, string> = {}): Record<string, string> => ({
  'Content-Type': 'application/json',
  ...extra,
});

export const authHeaders = (accessToken: string, extra: Record<string, string> = {}): Record<string, string> =>
  jsonHeaders({ Authorization: `Bearer ${accessToken}`, ...extra });

/**
 * Thin wrapper around the raw `performX` calls so specs don't repeat
 * URL/header construction for every request. Returns the raw supertest
 * response (non-2xx does not throw) so tests assert status explicitly.
 */
export class AutomationClient {
  constructor(private readonly accessToken: string) {}

  private headers() {
    return authHeaders(this.accessToken);
  }

  // ---- Domains ----------------------------------------------------------

  listDomains() {
    return performGet(automationUrl(), `${envPath()}/domains`, this.headers());
  }

  putDomain(definition: object) {
    return performPut(automationUrl(), `${envPath()}/domains`, definition, this.headers());
  }

  getDomain(key: string) {
    return performGet(automationUrl(), `${envPath()}/domains/${key}`, this.headers());
  }

  deleteDomain(key: string) {
    return performDelete(automationUrl(), `${envPath()}/domains/${key}`, this.headers());
  }

  // ---- Identity providers (under a domain) ------------------------------

  listIdentityProviders(domainKey: string) {
    return performGet(automationUrl(), `${envPath()}/domains/${domainKey}/identity-providers`, this.headers());
  }

  putIdentityProvider(domainKey: string, definition: object) {
    return performPut(automationUrl(), `${envPath()}/domains/${domainKey}/identity-providers`, definition, this.headers());
  }

  getIdentityProvider(domainKey: string, idpKey: string) {
    return performGet(automationUrl(), `${envPath()}/domains/${domainKey}/identity-providers/${idpKey}`, this.headers());
  }

  deleteIdentityProvider(domainKey: string, idpKey: string) {
    return performDelete(automationUrl(), `${envPath()}/domains/${domainKey}/identity-providers/${idpKey}`, this.headers());
  }

  // ---- Certificates (under a domain) ------------------------------------

  listCertificates(domainKey: string) {
    return performGet(automationUrl(), `${envPath()}/domains/${domainKey}/certificates`, this.headers());
  }

  putCertificate(domainKey: string, definition: object) {
    return performPut(automationUrl(), `${envPath()}/domains/${domainKey}/certificates`, definition, this.headers());
  }

  getCertificate(domainKey: string, certKey: string) {
    return performGet(automationUrl(), `${envPath()}/domains/${domainKey}/certificates/${certKey}`, this.headers());
  }

  deleteCertificate(domainKey: string, certKey: string) {
    return performDelete(automationUrl(), `${envPath()}/domains/${domainKey}/certificates/${certKey}`, this.headers());
  }

  // ---- Reporters (under a domain) ---------------------------------------

  listReporters(domainKey: string) {
    return performGet(automationUrl(), `${envPath()}/domains/${domainKey}/reporters`, this.headers());
  }

  putReporter(domainKey: string, definition: object) {
    return performPut(automationUrl(), `${envPath()}/domains/${domainKey}/reporters`, definition, this.headers());
  }

  getReporter(domainKey: string, reporterKey: string) {
    return performGet(automationUrl(), `${envPath()}/domains/${domainKey}/reporters/${reporterKey}`, this.headers());
  }

  deleteReporter(domainKey: string, reporterKey: string) {
    return performDelete(automationUrl(), `${envPath()}/domains/${domainKey}/reporters/${reporterKey}`, this.headers());
  }

  // ---- Generic escape hatches (auth + path) -----------------------------

  rawGet(path: string, headerOverride?: Record<string, string>) {
    return performGet(automationUrl(), path, headerOverride ?? this.headers());
  }
}
