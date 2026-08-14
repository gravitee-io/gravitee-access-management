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
import { setup } from '../../test-fixture';
import {
  IntrospectionClientAuthFixture,
  setupIntrospectionClientAuthFixture,
  UNKNOWN_CLIENT_CREDENTIALS,
} from './fixtures/introspection-client-auth-fixture';

// /oauth/introspect is the trust anchor a resource server relies on to decide whether a
// bearer token is usable. It shares the gateway's clientAuthHandler with /oauth/token
// (OAuth2Provider.java), but every client-authentication test in the suite targets the
// token endpoint only — so an authentication regression on this route would let an
// unauthenticated or foreign caller read the contents of someone else's token.
//
// These tests pin the two boundaries that matter:
//   1. the caller must present valid credentials for THIS domain (RFC 7662 §2.1)
//   2. a token only introspects as active on the domain that issued it

setup(120000);

let fixture: IntrospectionClientAuthFixture;

beforeAll(async () => {
  fixture = await setupIntrospectionClientAuthFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Introspection endpoint - client authentication', () => {
  it('accepts a resource server presenting valid credentials for its own domain', async () => {
    const { domainA } = fixture;
    const token = await fixture.issueToken(domainA);

    const response = await fixture
      .introspectWithBasicAuth(domainA, token, {
        clientId: domainA.resourceServer.settings.oauth.clientId,
        clientSecret: domainA.resourceServer.settings.oauth.clientSecret,
      })
      .expect(200);

    expect(response.body.active).toBe(true);
    expect(response.body.client_id).toBe(domainA.tokenIssuer.settings.oauth.clientId);
  });

  it('rejects a known client presenting the wrong client secret', async () => {
    const { domainA } = fixture;
    const token = await fixture.issueToken(domainA);

    const response = await fixture
      .introspectWithBasicAuth(domainA, token, {
        clientId: domainA.resourceServer.settings.oauth.clientId,
        clientSecret: 'this-is-not-the-right-secret',
      })
      .expect(401);

    expect(response.body.error).toBe('invalid_client');
  });

  it('rejects credentials for a client that does not exist', async () => {
    const { domainA } = fixture;
    const token = await fixture.issueToken(domainA);

    const response = await fixture.introspectWithBasicAuth(domainA, token, UNKNOWN_CLIENT_CREDENTIALS).expect(401);

    expect(response.body.error).toBe('invalid_client');
  });

  it('rejects a request that carries no credentials at all', async () => {
    const { domainA } = fixture;
    const token = await fixture.issueToken(domainA);

    const response = await fixture.introspectWithoutAuth(domainA, token).expect(401);

    expect(response.body.error).toBe('invalid_client');
  });

  it('rejects valid credentials that belong to a different domain', async () => {
    const { domainA, domainB } = fixture;
    const token = await fixture.issueToken(domainA);

    // domainB's resource server is a real client with a real secret — just not on domainA.
    const response = await fixture
      .introspectWithBasicAuth(domainA, token, {
        clientId: domainB.resourceServer.settings.oauth.clientId,
        clientSecret: domainB.resourceServer.settings.oauth.clientSecret,
      })
      .expect(401);

    expect(response.body.error).toBe('invalid_client');
  });
});

describe('Introspection endpoint - cross-domain token isolation', () => {
  it('reports a token from another domain as inactive even when the caller is authenticated', async () => {
    const { domainA, domainB } = fixture;
    const foreignToken = await fixture.issueToken(domainA);

    // The caller authenticates successfully against domainB; only the token is foreign.
    // This isolates token-scope enforcement from client authentication.
    const response = await fixture
      .introspectWithBasicAuth(domainB, foreignToken, {
        clientId: domainB.resourceServer.settings.oauth.clientId,
        clientSecret: domainB.resourceServer.settings.oauth.clientSecret,
      })
      .expect(200);

    expect(response.body.active).toBe(false);
  });

  it('still reports the domain-local token as active for the same caller', async () => {
    const { domainB } = fixture;
    const localToken = await fixture.issueToken(domainB);

    const response = await fixture
      .introspectWithBasicAuth(domainB, localToken, {
        clientId: domainB.resourceServer.settings.oauth.clientId,
        clientSecret: domainB.resourceServer.settings.oauth.clientSecret,
      })
      .expect(200);

    expect(response.body.active).toBe(true);
    expect(response.body.client_id).toBe(domainB.tokenIssuer.settings.oauth.clientId);
  });
});
