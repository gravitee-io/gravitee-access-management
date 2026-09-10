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
import jwt from 'jsonwebtoken';
import { createRemoteJWKSet, jwtVerify } from 'jose';
import { setup } from '../../test-fixture';
import { ID_JAG_JOSE_TYPE, ID_JAG_TOKEN_TYPE, IdJagFixture, setupIdJagFixture } from './fixtures/id-jag-fixture';
import { ID_TOKEN_TYPE } from './fixtures/token-exchange-fixture';

setup(300000);

let fixture: IdJagFixture;

const decode = (assertion: string) => jwt.decode(assertion, { complete: true }) as { header: any; payload: any };

const audienceParam = () => `&audience=${encodeURIComponent(fixture.audience)}`;

const resourceParam = (resource: string) => `&resource=${encodeURIComponent(resource)}`;

const errorOf = (response: any) => response.body.error;

const calendarWith = (scope?: string) =>
  audienceParam() + resourceParam(fixture.calendar.resource) + (scope ? `&scope=${encodeURIComponent(scope)}` : '');

const scopesOf = (scope: string) => scope.split(' ').sort();

const bothResourceServers = () =>
  fixture.setCrossAppAccess({
    enabled: true,
    resourceServers: [
      { trustDomainId: fixture.trustDomainId, resourceServerId: fixture.calendar.id, clientId: 'agent-at-acme-calendar' },
      { trustDomainId: fixture.trustDomainId, resourceServerId: fixture.mail.id, clientId: 'agent-at-acme-mail' },
    ],
  });

beforeAll(async () => {
  fixture = await setupIdJagFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('ID-JAG issuance - the assertion an agent takes to a partner', () => {
  it('should return the assertion in access_token with an N_A token type', async () => {
    const { accessToken } = await fixture.subjectTokens();

    const response = await fixture.requestIdJag(accessToken, audienceParam() + resourceParam(fixture.calendar.resource)).expect(200);

    expect(response.body.token_type).toBe('N_A');
    expect(response.body.issued_token_type).toBe(ID_JAG_TOKEN_TYPE);
    expect(response.body.access_token).toBeDefined();
    expect(response.body.expires_in).toBe(300);
    expect(response.body.refresh_token).toBeUndefined();
  });

  it('should carry the draft claim set, addressed to the partner rather than to AM', async () => {
    const { accessToken } = await fixture.subjectTokens();

    const response = await fixture.requestIdJag(accessToken, audienceParam() + resourceParam(fixture.calendar.resource)).expect(200);
    const { header, payload } = decode(response.body.access_token);

    expect(header.typ).toBe(ID_JAG_JOSE_TYPE);
    expect(payload.iss).toBe(fixture.oidc.issuer);
    expect(payload.sub).toBe(fixture.user.id);
    expect(payload.aud).toBe(fixture.audience);
    expect(payload.client_id).toBe('agent-at-acme-calendar');
    expect(payload.client_id).not.toBe(fixture.application.settings.oauth.clientId);
    expect(payload.resource).toBe(fixture.calendar.resource);
    expect(payload.jti).toBeDefined();
    expect(payload.iat).toBeDefined();
    expect(payload.exp).toBe(payload.iat + 300);
  });

  it('should be verifiable from the JWKS AM publishes at the issuer it advertises', async () => {
    const { accessToken } = await fixture.subjectTokens();

    const response = await fixture.requestIdJag(accessToken, audienceParam() + resourceParam(fixture.calendar.resource)).expect(200);
    const jwks = createRemoteJWKSet(new URL(fixture.oidc.jwks_uri));

    const { payload } = await jwtVerify(response.body.access_token, jwks, {
      issuer: fixture.oidc.issuer,
      audience: fixture.audience,
      typ: ID_JAG_JOSE_TYPE,
    });

    expect(payload.sub).toBe(fixture.user.id);
  });

  it('should accept an ID token as the subject token', async () => {
    const { idToken } = await fixture.subjectTokens();

    const response = await fixture
      .requestIdJag(idToken, audienceParam() + resourceParam(fixture.calendar.resource), ID_TOKEN_TYPE)
      .expect(200);

    expect(decode(response.body.access_token).payload.resource).toBe(fixture.calendar.resource);
  });

  it('should select the named resource server among those behind the audience', async () => {
    const { accessToken } = await fixture.subjectTokens();

    const response = await fixture.requestIdJag(accessToken, audienceParam() + resourceParam(fixture.mail.resource)).expect(200);
    const { payload } = decode(response.body.access_token);

    expect(payload.resource).toBe(fixture.mail.resource);
    expect(payload.client_id).toBe('agent-at-acme-mail');
  });
});

describe('ID-JAG issuance - resource is optional only when it is unambiguous', () => {
  beforeAll(bothResourceServers);

  it('should refuse an omitted resource while several resource servers survive', async () => {
    const { accessToken } = await fixture.subjectTokens();

    const response = await fixture.requestIdJag(accessToken, audienceParam()).expect(400);

    expect(errorOf(response)).toBe('invalid_request');
  });

  it('should default the resource when the application reaches exactly one resource server', async () => {
    await fixture.setCrossAppAccess({
      enabled: true,
      resourceServers: [{ trustDomainId: fixture.trustDomainId, resourceServerId: fixture.calendar.id, clientId: 'agent-at-acme-calendar' }],
    });
    const { accessToken } = await fixture.subjectTokens();

    const response = await fixture.requestIdJag(accessToken, audienceParam()).expect(200);

    expect(decode(response.body.access_token).payload.resource).toBe(fixture.calendar.resource);
  });

  it('should refuse a resource server that has since been deleted at domain level', async () => {
    await fixture.setCrossAppAccess({
      enabled: true,
      resourceServers: [{ trustDomainId: fixture.trustDomainId, resourceServerId: 'deleted-resource-server', clientId: 'stale-client' }],
    });
    const { accessToken } = await fixture.subjectTokens();

    const response = await fixture.requestIdJag(accessToken, audienceParam()).expect(400);

    expect(errorOf(response)).toBe('invalid_target');
  });
});

describe('ID-JAG issuance - every check fails closed', () => {
  beforeAll(bothResourceServers);

  it('should refuse a missing audience', async () => {
    const { accessToken } = await fixture.subjectTokens();

    const response = await fixture.requestIdJag(accessToken, resourceParam(fixture.calendar.resource)).expect(400);

    expect(errorOf(response)).toBe('invalid_request');
  });

  it('should refuse a repeated audience rather than resolving it to the first value', async () => {
    const { accessToken } = await fixture.subjectTokens();

    const response = await fixture
      .requestIdJag(accessToken, `${audienceParam()}&audience=${encodeURIComponent('https://auth.other.com')}${resourceParam(fixture.calendar.resource)}`)
      .expect(400);

    expect(errorOf(response)).toBe('invalid_request');
  });

  it('should refuse an audience matching no trusted domain', async () => {
    const { accessToken } = await fixture.subjectTokens();

    const response = await fixture
      .requestIdJag(accessToken, `&audience=${encodeURIComponent('https://auth.unknown.com')}`)
      .expect(400);

    expect(errorOf(response)).toBe('invalid_target');
  });

  it('should refuse a resource that does not sit behind the requested audience', async () => {
    const { accessToken } = await fixture.subjectTokens();

    const response = await fixture.requestIdJag(accessToken, audienceParam() + resourceParam('https://elsewhere.example.com')).expect(400);

    expect(errorOf(response)).toBe('invalid_target');
  });

  it('should refuse an application whose Cross App Access block is disabled', async () => {
    await fixture.setCrossAppAccess({
      enabled: false,
      resourceServers: [{ trustDomainId: fixture.trustDomainId, resourceServerId: fixture.calendar.id, clientId: 'agent-at-acme-calendar' }],
    });
    const { accessToken } = await fixture.subjectTokens();

    const response = await fixture.requestIdJag(accessToken, audienceParam() + resourceParam(fixture.calendar.resource)).expect(400);

    expect(errorOf(response)).toBe('unauthorized_client');
  });
});

describe("ID-JAG issuance - scopes travel in the partner's vocabulary", () => {
  beforeAll(bothResourceServers);

  it('should carry a requested domain scope under the name the trusted domain maps it to', async () => {
    const { accessToken } = await fixture.subjectTokens();

    const response = await fixture.requestIdJag(accessToken, calendarWith('profile')).expect(200);

    expect(decode(response.body.access_token).payload.scope).toBe('read:profile');
    expect(response.body.scope).toBe('read:profile');
  });

  it('should grant the full mapped set when the request names no scope', async () => {
    const { accessToken } = await fixture.subjectTokens();

    const response = await fixture.requestIdJag(accessToken, calendarWith()).expect(200);

    expect(scopesOf(decode(response.body.access_token).payload.scope)).toEqual(['read:email', 'read:profile']);
    expect(scopesOf(response.body.scope)).toEqual(['read:email', 'read:profile']);
  });

  it('should refuse a requested scope with no mapping rather than narrow the assertion', async () => {
    const { accessToken } = await fixture.subjectTokens();

    const response = await fixture.requestIdJag(accessToken, calendarWith('profile openid')).expect(400);

    expect(errorOf(response)).toBe('invalid_scope');
  });

  it('should audit a refused scope under the name the agent asked for', async () => {
    const { accessToken } = await fixture.subjectTokens();
    const unmappedScope = `unmapped-${Date.now()}`;
    await fixture.requestIdJag(accessToken, calendarWith(unmappedScope)).expect(400);

    const audit = await fixture.awaitTokenAudit('FAILURE', (detail) => JSON.stringify(detail).includes(unmappedScope));

    expect(audit.outcome.message).toContain(`"SCOPE":"${unmappedScope}"`);
    expect(audit.outcome.message).toContain(ID_JAG_TOKEN_TYPE);
  });

  it('should grant the same scopes whether the subject token is an ID token or an access token', async () => {
    const { accessToken, idToken } = await fixture.subjectTokens('openid');

    const fromAccessToken = await fixture.requestIdJag(accessToken, calendarWith('email')).expect(200);
    const fromIdToken = await fixture.requestIdJag(idToken, calendarWith('email'), ID_TOKEN_TYPE).expect(200);

    expect(decode(fromAccessToken.body.access_token).payload.scope).toBe('read:email');
    expect(decode(fromIdToken.body.access_token).payload.scope).toBe('read:email');
  });

  it("should audit the security domain's own scope names, not the partner's", async () => {
    const { accessToken } = await fixture.subjectTokens();
    const response = await fixture.requestIdJag(accessToken, calendarWith('profile')).expect(200);
    const assertionId = decode(response.body.access_token).payload.jti;

    const audit = await fixture.awaitTokenAudit('SUCCESS', (detail) => JSON.stringify(detail).includes(assertionId));

    expect(audit.outcome.message).toContain('"path":"/SCOPE","value":"profile"');
    expect(JSON.stringify(audit)).not.toContain('read:profile');
  });
});

describe('ID-JAG issuance - the assertion never outlives its subject token', () => {
  it('should clamp the exp claim, not only the advertised lifetime', async () => {
    await fixture.setCrossAppAccess(
      {
        enabled: true,
        resourceServers: [{ trustDomainId: fixture.trustDomainId, resourceServerId: fixture.calendar.id, clientId: 'agent-at-acme-calendar' }],
      },
      36000,
    );
    const { accessToken } = await fixture.subjectTokens();
    const subjectExp = decode(accessToken).payload.exp;

    const response = await fixture.requestIdJag(accessToken, audienceParam()).expect(200);
    const { payload } = decode(response.body.access_token);

    expect(payload.exp).toBeLessThanOrEqual(subjectExp);
    expect(payload.exp - payload.iat).toBeLessThan(36000);
    expect(response.body.expires_in).toBe(payload.exp - payload.iat);
  });
});

describe('ID-JAG issuance - the audit log answers who was granted access to which partner', () => {
  beforeAll(bothResourceServers);

  it('should audit an issuance in the token stream with the audience and the resolved resource', async () => {
    const { accessToken } = await fixture.subjectTokens();
    const response = await fixture.requestIdJag(accessToken, audienceParam() + resourceParam(fixture.mail.resource)).expect(200);
    const assertionId = decode(response.body.access_token).payload.jti;

    const audit = await fixture.awaitTokenAudit('SUCCESS', (detail) => JSON.stringify(detail).includes(assertionId));

    expect(audit.type).toBe('TOKEN_CREATED');
    expect(audit.target.id).toBe(fixture.user.id);
    expect(JSON.stringify(audit)).toContain(fixture.audience);
    expect(JSON.stringify(audit)).toContain(fixture.mail.resource);
    expect(JSON.stringify(audit)).toContain(ID_JAG_TOKEN_TYPE);
  });

  it('should audit a denial with the same context', async () => {
    const { accessToken } = await fixture.subjectTokens();
    const unknownAudience = 'https://auth.unaudited.com';
    await fixture.requestIdJag(accessToken, `&audience=${encodeURIComponent(unknownAudience)}`).expect(400);

    const audit = await fixture.awaitTokenAudit('FAILURE', (detail) => JSON.stringify(detail).includes(unknownAudience));

    expect(audit.type).toBe('TOKEN_CREATED');
    expect(audit.outcome.message).toContain(ID_JAG_TOKEN_TYPE);
    expect(audit.outcome.message).toContain(unknownAudience);
  });
});

describe('ID-JAG issuance - the domain-level switch is the single place it is enabled', () => {
  beforeAll(bothResourceServers);

  afterAll(async () => {
    await fixture.setDomainAllowsIdJag(true);
  });

  it('should refuse an ID-JAG on a domain that does not permit that requested token type', async () => {
    const { accessToken } = await fixture.subjectTokens();
    await fixture.setDomainAllowsIdJag(false);

    const response = await fixture.requestIdJag(accessToken, audienceParam() + resourceParam(fixture.calendar.resource)).expect(400);

    expect(errorOf(response)).toBe('invalid_request');
  });
});
