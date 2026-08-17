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
import { delay } from '@utils-commands/misc';
import {
  IntrospectionTokenValidityFixture,
  setupIntrospectionTokenValidityFixture,
  SHORT_TOKEN_VALIDITY_SECONDS,
  tamperWithPayload,
} from './fixtures/introspection-token-validity-fixture';

// A resource server treats `active: true` as permission to serve the request, so every way a
// token can stop being usable has to surface as `active: false`. IntrospectionServiceImpl
// funnels *all* errors into that single response, which means a regression that throws instead
// (or that lets a dead token through) is invisible unless the endpoint itself is exercised —
// the service-layer unit tests mock TokenService, so decode and signature verification never run.

setup(180000);

let fixture: IntrospectionTokenValidityFixture;

beforeAll(async () => {
  fixture = await setupIntrospectionTokenValidityFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Introspection - unusable tokens report inactive', () => {
  it('reports a freshly issued token as active', async () => {
    const token = await fixture.issueToken();

    const body = await fixture.introspect(token);

    expect(body.active).toBe(true);
    expect(body.client_id).toBe(fixture.issuer.settings.oauth.clientId);
  });

  it('reports an opaque string that is not a token as inactive', async () => {
    const body = await fixture.introspect('this-is-not-a-token-at-all');

    expect(body.active).toBe(false);
  });

  it('reports a token whose payload was rewritten after signing as inactive', async () => {
    const token = await fixture.issueToken();
    const tampered = tamperWithPayload(token);
    expect(tampered).not.toBe(token);

    const body = await fixture.introspect(tampered);

    expect(body.active).toBe(false);
  });
});

describe('Introspection - expired tokens', () => {
  it('reports a token as inactive once its lifetime has elapsed', async () => {
    const token = await fixture.issueShortLivedToken();

    await delay((SHORT_TOKEN_VALIDITY_SECONDS + 2) * 1000);

    const body = await fixture.introspect(token);

    expect(body.active).toBe(false);
  });
});

describe('Introspection - revoked tokens', () => {
  it('reports a revoked access token as inactive', async () => {
    const token = await fixture.issueToken();
    expect((await fixture.introspect(token)).active).toBe(true);

    await fixture.revokeToken(token, fixture.issuer);
    const body = await fixture.introspect(token);

    expect(body.active).toBe(false);
  });
});
