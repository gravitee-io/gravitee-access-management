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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, startDomain } from '@management-commands/domain-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';
import { Application } from '@management-models/Application';
import { Domain } from '@management-models/Domain';
import { jira } from '@specs-utils/jira';
import request from 'supertest';
import { setup } from '../../test-fixture';
import { patchDomainRaw } from '../../gateway/token-exchange/fixtures/trusted-issuer-fixture';
import { TOKEN_EXCHANGE_TEST } from '../../gateway/token-exchange/fixtures/token-exchange-fixture';

setup();

/**
 * A mapping may read from a subject token an external issuer signed, so a mapper that writes a
 * protocol claim would let that issuer redefine the identity of the exchanged token. The Management
 * API must refuse those target names at both the domain and the application level.
 */
const RESERVED_CLAIMS = [
  'gis',
  'sub',
  'iss',
  'aud',
  'exp',
  'iat',
  'nbf',
  'jti',
  'act',
  'client_id',
  'scope',
  // authentication context the relying party makes security decisions on
  'auth_time',
  'nonce',
  'acr',
  'amr',
  'azp',
  // agent identity AM writes only when still absent, so a mapping here suppresses it
  'client_profile',
  'sub_profile',
];

let accessToken: string;
let domain: Domain;
let application: Application;

const patchApplicationRaw = (domainId: string, appId: string, token: string, body: Record<string, unknown>): request.Test =>
  request(getDomainManagerUrl(domainId))
    .patch(`/applications/${appId}`)
    .set('Authorization', `Bearer ${token}`)
    .set('Content-Type', 'application/json')
    .send(body);

const domainSettings = (claimMappings: unknown) => ({
  tokenExchangeSettings: {
    enabled: true,
    allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
    allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
    allowImpersonation: true,
    allowDelegation: false,
    tokenExchangeOAuthSettings: { inherited: false, claimMappings },
  },
});

const applicationSettings = (claimMappings: unknown) => ({
  settings: { oauth: { tokenExchangeOAuthSettings: { inherited: false, claimMappings } } },
});

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();

  const createdDomain = await createDomain(accessToken, uniqueName('tx-mapper-valid', true), 'Claims mapper validation');
  domain = await startDomain(createdDomain.id, accessToken);

  application = await createTestApp(uniqueName('tx-mapper-valid-app', true), domain, accessToken, 'service', {
    settings: {
      oauth: {
        grantTypes: ['urn:ietf:params:oauth:grant-type:token-exchange'],
        scopeSettings: [{ scope: 'openid', defaultScope: true }],
      },
    },
  });
});

afterAll(async () => {
  if (domain?.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});

describe('Token exchange claims mapper validation - application level', () => {
  it.each(RESERVED_CLAIMS)(jira`should refuse a mapping onto the reserved claim "%s" ${'AM-7541'}`, async (reserved) => {
    const response = await patchApplicationRaw(
      domain.id,
      application.id,
      accessToken,
      applicationSettings([{ source: 'SUBJECT_TOKEN', sourceClaim: 'claim_id', tokenClaim: reserved }]),
    ).expect(400);

    expect(response.body.message).toEqual(`Invalid token exchange claim mappings: [${reserved}]`);
  });

  it(jira`should name every reserved claim it refuses ${'AM-7541'}`, async () => {
    const response = await patchApplicationRaw(
      domain.id,
      application.id,
      accessToken,
      applicationSettings([
        { source: 'SUBJECT_TOKEN', sourceClaim: 'a', tokenClaim: 'sub' },
        { source: 'SUBJECT_TOKEN', sourceClaim: 'b', tokenClaim: 'business_claim_id' },
        { source: 'ACTOR_TOKEN', sourceClaim: 'c', tokenClaim: 'iss' },
      ]),
    ).expect(400);

    expect(response.body.message).toContain('sub');
    expect(response.body.message).toContain('iss');
    expect(response.body.message).not.toContain('business_claim_id');
  });

  it('should refuse two mappings onto the same target claim', async () => {
    const response = await patchApplicationRaw(
      domain.id,
      application.id,
      accessToken,
      applicationSettings([
        { source: 'SUBJECT_TOKEN', sourceClaim: 'claim_id', tokenClaim: 'business_claim_id' },
        { source: 'ACTOR_TOKEN', sourceClaim: 'agent_id', tokenClaim: 'business_claim_id' },
      ]),
    ).expect(400);

    expect(response.body.message).toEqual('Duplicate token exchange claim mappings: [business_claim_id]');
  });

  it(jira`should accept an ordinary target claim and store the mapping ${'AM-7541'}`, async () => {
    const mapping = { source: 'SUBJECT_TOKEN', sourceClaim: 'claim_id', tokenClaim: 'business_claim_id' };

    const response = await patchApplicationRaw(domain.id, application.id, accessToken, applicationSettings([mapping])).expect(200);

    expect(response.body.settings.oauth.tokenExchangeOAuthSettings.claimMappings).toEqual([
      { source: 'subject_token', sourceClaim: 'claim_id', tokenClaim: 'business_claim_id' },
    ]);
  });
});

describe('Token exchange claims mapper validation - domain level', () => {
  it.each(RESERVED_CLAIMS)(jira`should refuse a domain mapping onto the reserved claim "%s" ${'AM-7541'}`, async (reserved) => {
    const response = await patchDomainRaw(
      domain.id,
      accessToken,
      domainSettings([{ source: 'SUBJECT_TOKEN', sourceClaim: 'claim_id', tokenClaim: reserved }]),
    ).expect(400);

    expect(response.body.message).toEqual(`Invalid token exchange claim mappings: [${reserved}]`);
  });

  it('should refuse two domain mappings onto the same target claim', async () => {
    const response = await patchDomainRaw(
      domain.id,
      accessToken,
      domainSettings([
        { source: 'SUBJECT_TOKEN', sourceClaim: 'claim_id', tokenClaim: 'agent_email' },
        { source: 'ACTOR_TOKEN', sourceClaim: 'email', tokenClaim: 'agent_email' },
      ]),
    ).expect(400);

    expect(response.body.message).toEqual('Duplicate token exchange claim mappings: [agent_email]');
  });

  it(jira`should accept an ordinary target claim on the domain defaults ${'AM-7541'}`, async () => {
    const response = await patchDomainRaw(
      domain.id,
      accessToken,
      domainSettings([{ source: 'ACTOR_TOKEN', sourceClaim: 'email', tokenClaim: 'agent_email' }]),
    ).expect(200);

    expect(response.body.tokenExchangeSettings.tokenExchangeOAuthSettings.claimMappings).toEqual([
      { source: 'actor_token', sourceClaim: 'email', tokenClaim: 'agent_email' },
    ]);
  });
});
