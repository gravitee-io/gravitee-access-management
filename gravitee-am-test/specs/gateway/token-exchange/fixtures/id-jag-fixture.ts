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

import { expect } from '@jest/globals';
import request from 'supertest';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, startDomain, waitForOidcReady } from '@management-commands/domain-management-commands';
import { createDomain } from '@management-commands/domain-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { createTrustedDomain } from '../../../management/domain/fixtures/cross-app-access-fixture';
import { getAllCertificates } from '@management-commands/certificate-management-commands';
import { updateApplication } from '@management-commands/application-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';
import { retryUntil } from '@utils-commands/retry';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { User } from '@management-models/User';
import { createTrustedIssuerKeyMaterial } from './trusted-issuer-jwt-helper';
import { TOKEN_EXCHANGE_TEST } from './token-exchange-fixture';

export const ID_JAG_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:id-jag';
export const ID_JAG_JOSE_TYPE = 'oauth-id-jag+jwt';

export interface IdJagResourceServer {
  id: string;
  name: string;
  resource: string;
}

export interface IdJagFixture {
  accessToken: string;
  domain: Domain;
  application: Application;
  user: User;
  oidc: Record<string, any>;
  basicAuth: string;
  audience: string;
  calendar: IdJagResourceServer;
  mail: IdJagResourceServer;
  trustDomainId: string;
  subjectTokens: (scope?: string) => Promise<{ accessToken: string; idToken?: string; expiresIn: number }>;
  requestIdJag: (subjectToken: string, extraParams?: string, subjectTokenType?: string) => request.Test;
  setCrossAppAccess: (crossAppAccessSettings: Record<string, unknown>, idJagValiditySeconds?: number) => Promise<void>;
  setDomainAllowsIdJag: (allowed: boolean) => Promise<void>;
  awaitTokenAudit: (status: 'SUCCESS' | 'FAILURE', matches: (detail: any) => boolean) => Promise<any>;
  cleanUp: () => Promise<void>;
}

const DOMAIN_SCOPES = [
  { scope: 'openid', defaultScope: true },
  { scope: 'profile', defaultScope: true },
];

export const setupIdJagFixture = async (): Promise<IdJagFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('id-jag', true), 'ID-JAG issuance');

  const patchTokenExchange = (allowedRequestedTokenTypes: string[]) =>
    request(getDomainManagerUrl(domain.id))
      .patch('')
      .set('Authorization', `Bearer ${accessToken}`)
      .set('Content-Type', 'application/json')
      .send({
        tokenExchangeSettings: {
          enabled: true,
          allowImpersonation: true,
          allowDelegation: false,
          allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
          allowedRequestedTokenTypes,
        },
      })
      .expect(200);

  await patchTokenExchange([...TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES, ID_JAG_TOKEN_TYPE]);

  const audience = 'https://auth.acme.com/id-jag';
  const trustedKey = createTrustedIssuerKeyMaterial();
  const trustDomain: any = await createTrustedDomain(domain.id, accessToken, {
    name: uniqueName('acme-authority'),
    domainIdentifier: audience,
    keyMaterial: { source: 'PEM', certificate: trustedKey.certificatePem },
    crossAppAccess: {
      enabled: true,
      resourceServers: [
        { name: 'Calendar', resource: 'https://calendar.acme.com' },
        { name: 'Mail', resource: 'https://mail.acme.com' },
      ],
      scopeMappings: { profile: 'read:profile', email: 'read:email' },
    },
  });
  const [calendar, mail] = trustDomain.crossAppAccess.resourceServers as IdJagResourceServer[];

  const idpSet = await getAllIdps(domain.id, accessToken);
  const defaultIdp = idpSet.values().next().value;

  const certificates = await getAllCertificates(domain.id, accessToken);
  const certificate = certificates[0].id;

  const application = await createTestApp(uniqueName('agent', true), domain, accessToken, 'WEB', {
    certificate,
    settings: {
      oauth: {
        redirectUris: [TOKEN_EXCHANGE_TEST.REDIRECT_URI],
        grantTypes: ['password', 'refresh_token', 'urn:ietf:params:oauth:grant-type:token-exchange'],
        scopeSettings: DOMAIN_SCOPES,
        idJagValiditySeconds: 300,
        crossAppAccessSettings: {
          enabled: true,
          resourceServers: [
            { trustDomainId: trustDomain.id, resourceServerId: calendar.id, clientId: 'agent-at-acme-calendar' },
            { trustDomainId: trustDomain.id, resourceServerId: mail.id, clientId: 'agent-at-acme-mail' },
          ],
        },
      },
    },
    identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
  });

  const startedDomain = await startDomain(domain.id, accessToken);
  const oidcResponse = await waitForOidcReady(startedDomain.hrid, { timeoutMs: 30000, intervalMs: 500 });
  expect(oidcResponse.status).toBe(200);
  const oidc = oidcResponse.body;

  const user = await buildCreateAndTestUser(domain.id, accessToken, 0);
  const basicAuth = applicationBase64Token(application);

  const subjectTokens = async (scope = 'openid%20profile') => {
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=password&username=${user.username}&password=${TOKEN_EXCHANGE_TEST.USER_PASSWORD}&scope=${scope}`,
      { 'Content-type': 'application/x-www-form-urlencoded', Authorization: `Basic ${basicAuth}` },
    ).expect(200);
    return { accessToken: response.body.access_token, idToken: response.body.id_token, expiresIn: response.body.expires_in };
  };

  const requestIdJag = (
    subjectToken: string,
    extraParams = '',
    subjectTokenType = 'urn:ietf:params:oauth:token-type:access_token',
  ): request.Test =>
    performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectToken}` +
        `&subject_token_type=${subjectTokenType}` +
        `&requested_token_type=${ID_JAG_TOKEN_TYPE}` +
        extraParams,
      { 'Content-type': 'application/x-www-form-urlencoded', Authorization: `Basic ${basicAuth}` },
    );

  const setCrossAppAccess = async (crossAppAccessSettings: Record<string, unknown>, idJagValiditySeconds = 300) => {
    await waitForSyncAfter(domain.id, () =>
      updateApplication(domain.id, accessToken, {
        certificate,
        settings: {
          oauth: {
            redirectUris: [TOKEN_EXCHANGE_TEST.REDIRECT_URI],
            grantTypes: ['password', 'refresh_token', 'urn:ietf:params:oauth:grant-type:token-exchange'],
            scopeSettings: DOMAIN_SCOPES,
            idJagValiditySeconds,
            crossAppAccessSettings,
          },
        },
      } as any, application.id),
    );
  };

  const setDomainAllowsIdJag = async (allowed: boolean) => {
    const allowedRequestedTokenTypes = allowed
      ? [...TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES, ID_JAG_TOKEN_TYPE]
      : [...TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES];
    await waitForSyncAfter(domain.id, () => patchTokenExchange(allowedRequestedTokenTypes));
  };

  const listTokenAudits = async (status: string) => {
    const response = await request(getDomainManagerUrl(domain.id))
      .get(`/audits?type=TOKEN_CREATED&status=${status}&size=20`)
      .set('Authorization', `Bearer ${accessToken}`);
    return response.status === 200 ? (response.body.data ?? []) : [];
  };

  const readTokenAudit = async (auditId: string) => {
    const response = await request(getDomainManagerUrl(domain.id))
      .get(`/audits/${auditId}`)
      .set('Authorization', `Bearer ${accessToken}`);
    return response.status === 200 ? response.body : null;
  };

  const awaitTokenAudit = async (status: 'SUCCESS' | 'FAILURE', matches: (detail: any) => boolean) => {
    const matching = async () => {
      const details = await Promise.all((await listTokenAudits(status)).map((audit) => readTokenAudit(audit.id)));
      return details.filter((detail) => detail !== null).find(matches) ?? null;
    };
    return retryUntil(matching, (found) => found !== null, { timeoutMillis: 30000, intervalMillis: 500 });
  };

  return {
    accessToken,
    domain: startedDomain,
    application,
    user,
    oidc,
    basicAuth,
    audience,
    calendar,
    mail,
    trustDomainId: trustDomain.id,
    subjectTokens,
    requestIdJag,
    setCrossAppAccess,
    setDomainAllowsIdJag,
    awaitTokenAudit,
    cleanUp: () => safeDeleteDomain(domain.id, accessToken),
  };
};
