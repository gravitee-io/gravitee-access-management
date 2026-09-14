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
import { setup } from '../../test-fixture';
import { ID_JAG_TOKEN_TYPE, IdJagFixture, setupIdJagFixture } from './fixtures/id-jag-fixture';

setup(300000);

const EMAIL = "{#context.attributes['user'].email}";
const USERNAME = "{#context.attributes['user'].username}";
const NOTHING = "{#context.attributes['user'].additionalInformation['acme_id']}";
const BROKEN = "{#context.attributes['user'].noSuchProperty}";

let fixture: IdJagFixture;

const decode = (token: string) => jwt.decode(token) as any;

const idJagClaim = (claimName: string, claimValue: string) => ({ tokenType: 'ID_JAG', claimName, claimValue });

const configure = async (audSubMapping: string, tokenCustomClaims: Record<string, unknown>[] = []) => {
  await fixture.setAudSubMapping(audSubMapping);
  await fixture.setCrossAppAccess(fixture.bothResourceServerSettings(), 300, tokenCustomClaims);
};

const requestAssertion = async (expectedStatus: number) => {
  const { accessToken } = await fixture.subjectTokens();
  const target = `&audience=${encodeURIComponent(fixture.audience)}&resource=${encodeURIComponent(fixture.calendar.resource)}`;
  return fixture.requestIdJag(accessToken, target).expect(expectedStatus);
};

const mintedClaims = async () => decode((await requestAssertion(200)).body.access_token);

beforeAll(async () => {
  fixture = await setupIdJagFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe("ID-JAG issuance - aud_sub from the trusted domain's expression", () => {
  it('should carry the value the expression produces over the user profile', async () => {
    await configure(EMAIL);

    expect((await mintedClaims()).aud_sub).toBe(fixture.user.email);
  });

  it('should omit the claim and still issue when the expression yields nothing', async () => {
    await configure(NOTHING);

    expect(await mintedClaims()).not.toHaveProperty('aud_sub');
  });

  it('should refuse issuance when the expression fails outright, and audit it', async () => {
    await configure(BROKEN);

    const response = await requestAssertion(400);

    expect(response.body.error).toBe('invalid_grant');
    const audit = await fixture.awaitTokenAudit('FAILURE', (detail) => JSON.stringify(detail).includes('aud_sub mapping'));
    expect(audit.outcome.message).toContain(fixture.audience);
    expect(audit.outcome.message).toContain(ID_JAG_TOKEN_TYPE);
  });
});

describe("ID-JAG issuance - the application's own ID-JAG claims", () => {
  it('should be applied to the assertion', async () => {
    await configure(EMAIL, [idJagClaim('tenant', 'acme-tenant')]);

    const claims = await mintedClaims();

    expect(claims.tenant).toBe('acme-tenant');
    expect(claims.aud_sub).toBe(fixture.user.email);
  });

  it("should let an aud_sub claim take precedence over the trusted domain's expression", async () => {
    await configure(BROKEN, [idJagClaim('aud_sub', USERNAME)]);

    expect((await mintedClaims()).aud_sub).toBe(fixture.user.username);
  });

  it('should let a sub claim override the AM user id', async () => {
    await configure(EMAIL, [idJagClaim('sub', USERNAME)]);

    expect((await mintedClaims()).sub).toBe(fixture.user.username);
  });

  it("should ignore a claim naming one of the assertion's own identity claims", async () => {
    await configure(
      EMAIL,
      ['iss', 'aud', 'client_id', 'jti', 'exp', 'iat'].map((claimName) => idJagClaim(claimName, 'forged')),
    );

    const claims = await mintedClaims();

    expect(claims.iss).toBe(fixture.oidc.issuer);
    expect(claims.aud).toBe(fixture.audience);
    expect(claims.client_id).toBe('agent-at-acme-calendar');
    expect(claims.jti).not.toBe('forged');
    expect(typeof claims.exp).toBe('number');
    expect(typeof claims.iat).toBe('number');
  });
});

describe('Custom claims on access tokens and ID tokens', () => {
  it('should stay lenient when their expression fails', async () => {
    await configure(EMAIL, [
      { tokenType: 'ACCESS_TOKEN', claimName: 'broken', claimValue: BROKEN },
      { tokenType: 'ID_TOKEN', claimName: 'broken', claimValue: BROKEN },
    ]);

    const { accessToken, idToken } = await fixture.subjectTokens();

    expect(decode(accessToken)).not.toHaveProperty('broken');
    expect(decode(idToken)).not.toHaveProperty('broken');
  });
});
