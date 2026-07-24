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
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { setup } from '../../test-fixture';
import {
  setupTokenExchangeFixture,
  TokenExchangeFixture,
  TOKEN_EXCHANGE_TEST,
} from './fixtures/token-exchange-fixture';

setup(120000);

let defaultFixture: TokenExchangeFixture;
let delegationFixture: TokenExchangeFixture;

beforeAll(async () => {
  // Setup default fixture with all token types allowed (impersonation only by default)
  defaultFixture = await setupTokenExchangeFixture();

  // Setup delegation fixture with delegation enabled
  delegationFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'token-exchange-delegation',
    domainDescription: 'Token exchange with delegation',
    clientName: 'token-exchange-delegation-client',
    allowImpersonation: true,
    allowDelegation: true,
    allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
    maxDelegationDepth: 3,
  });
});

afterAll(async () => {
  if (defaultFixture) {
    await defaultFixture.cleanup();
  }
  if (delegationFixture) {
    await delegationFixture.cleanup();
  }
});

describe('Token Exchange with invalid tokens', () => {
  it('should reject a subject token with a tampered signature', async () => {
    const { oidc, basicAuth, obtainSubjectToken } = defaultFixture;

    // Given: obtain a valid subject token, then corrupt its signature.
    const { accessToken: validToken } = await obtainSubjectToken('openid%20profile');
    const parts = validToken.split('.');
    const tamperedToken = `${parts[0]}.${parts[1]}.invalidsignatureXXXXXXXXXXX`;

    // When: present the tampered token for exchange.
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${tamperedToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    // Then: invalid_request with a generic description.
    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toBe('The presented token is invalid');
  });

  it('should reject a tampered actor token in delegation', async () => {
    const { oidc, basicAuth, obtainSubjectToken, obtainActorToken } = delegationFixture;

    // Given: valid subject and actor tokens; tamper the actor token's signature.
    const { accessToken: subjectToken } = await obtainSubjectToken('openid%20profile');
    const { accessToken: validActorToken } = await obtainActorToken('openid%20profile');
    const parts = validActorToken.split('.');
    const tamperedActorToken = `${parts[0]}.${parts[1]}.invalidsignatureXXXXXXXXXXX`;

    // When: present the tampered actor token for delegation exchange.
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
      `&subject_token=${subjectToken}` +
      `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
      `&actor_token=${tamperedActorToken}` +
      `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    // Then: invalid_request with a generic description.
    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toBe('The presented token is invalid');
  });
});
