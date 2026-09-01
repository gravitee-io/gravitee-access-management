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
import { jira } from '@specs-utils/jira';
import { JWT_FORMAT } from '@specs-utils/jwt-format';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { setup } from '../../test-fixture';
import { ArbitraryAuthMethodFixture, AuthMethodApp, setupArbitraryAuthMethodFixture } from './fixtures/arbitrary-auth-method-fixture';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';

setup(200000);

/**
 * AM-2232 / UC-AM58 — an application left without a fixed client authentication method.
 *
 * Every method is covered elsewhere with that method fixed on the application. What was untested
 * is the arbitrary option itself: one application, no method set, accepting whichever the incoming
 * request uses. `ClientBasicAuthProvider.canHandle` allows it when the stored method is null or
 * empty and the matching credentials are present.
 *
 * The two applications live in one domain, so the comparison differs only in that setting.
 */
let fixture: ArbitraryAuthMethodFixture;

beforeAll(async () => {
  fixture = await setupArbitraryAuthMethodFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const FORM = { 'Content-type': 'application/x-www-form-urlencoded' };

/** Credentials in the Authorization header — client_secret_basic. */
const withAuthorizationHeader = (app: AuthMethodApp) =>
  performPost(fixture.oidc.token_endpoint, '', 'grant_type=client_credentials', {
    ...FORM,
    Authorization: 'Basic ' + getBase64BasicAuth(app.clientId, app.clientSecret),
  });

/** Credentials in the request body — client_secret_post. */
const withRequestBody = (app: AuthMethodApp) =>
  performPost(
    fixture.oidc.token_endpoint,
    '',
    `grant_type=client_credentials&client_id=${encodeURIComponent(app.clientId)}&client_secret=${encodeURIComponent(app.clientSecret)}`,
    FORM,
  );

const JWT_BEARER = 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer';

/** A signed assertion carrying the credentials — client_secret_jwt. */
const withSignedAssertion = (app: AuthMethodApp) => {
  const now = Math.floor(Date.now() / 1000);
  const assertion = jwt.sign(
    {
      iss: app.clientId,
      sub: app.clientId,
      aud: fixture.oidc.token_endpoint,
      jti: crypto.randomUUID(),
      iat: now,
      exp: now + 300,
    },
    app.clientSecret,
    { algorithm: 'HS256' },
  );

  return performPost(
    fixture.oidc.token_endpoint,
    '',
    `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(JWT_BEARER)}&client_assertion=${encodeURIComponent(
      assertion,
    )}`,
    FORM,
  );
};

describe('An application with no authentication method set', () => {
  it(jira`is stored with an empty method rather than a default ${'AM-2232'}`, async () => {
    // Anchors the rest of the file. Omitting the field on create is not the same thing: the
    // management API then stores client_secret_basic, and the application would be fixed to one
    // method while appearing unset — every test below would pass against the wrong subject.
    expect(fixture.unsetAppStoredMethod).toEqual('');
  });

  it(jira`accepts credentials in the authorization header ${'AM-2232'}`, async () => {
    const response = await withAuthorizationHeader(fixture.unsetApp);

    expect(response.status).toEqual(200);
    expect(response.body.access_token).toMatch(JWT_FORMAT);
  });

  it(jira`also accepts credentials in the request body ${'AM-2232'}`, async () => {
    const response = await withRequestBody(fixture.unsetApp);

    expect(response.status).toEqual(200);
    expect(response.body.access_token).toMatch(JWT_FORMAT);
  });

  it(jira`also accepts a signed assertion ${'AM-7591'}`, async () => {
    const response = await withSignedAssertion(fixture.unsetApp);

    expect(response.status).toEqual(200);
    expect(response.body.access_token).toMatch(JWT_FORMAT);
  });
});

describe('An application fixed to one authentication method', () => {
  it(jira`accepts its own method ${'AM-2232'}`, async () => {
    const response = await withAuthorizationHeader(fixture.fixedApp);

    expect(response.status).toEqual(200);
    expect(response.body.access_token).toMatch(JWT_FORMAT);
  });

  it(jira`refuses a different one ${'AM-2232'}`, async () => {
    const response = await withRequestBody(fixture.fixedApp);

    // The same request the unset application accepts above. Same domain, same identity provider,
    // same credentials — only the setting differs, which is what makes the option meaningful.
    expect(response.status).toEqual(401);
    expect(response.body.error).toEqual('invalid_client');
  });

  it(jira`refuses a signed assertion ${'AM-7591'}`, async () => {
    const response = await withSignedAssertion(fixture.fixedApp);

    expect(response.status).toEqual(401);
    expect(response.body.error).toEqual('invalid_client');
  });
});
