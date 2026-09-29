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
import { parseJwt } from '@api-fixtures/jwt';
import { setup } from '../../test-fixture';
import { setupTokenExchangeFixture, TokenExchangeFixture, TOKEN_EXCHANGE_TEST } from './fixtures/token-exchange-fixture';

setup(120000);

let fixture: TokenExchangeFixture;

beforeAll(async () => {
  fixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'token-exchange-lightweight',
    domainDescription: 'Token exchange delegation with Lightweight JWT',
    clientName: 'token-exchange-lightweight-client',
    allowImpersonation: true,
    allowDelegation: true,
    allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
    maxDelegationDepth: 3,
    lightweightJwtSettings: { enabled: true },
    tokenCustomClaims: [
      {
        claimName: 'tx_actor_jti',
        claimValue: "{#context.attributes['token_exchange']['actor']['actor_token_claims']['jti']}",
        tokenType: 'ACCESS_TOKEN',
      },
    ],
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Token Exchange - Lightweight JWT', () => {
  it('should keep the act claim and remove the actor custom claim from a delegated token', async () => {
    const { accessToken: subjectToken } = await fixture.obtainSubjectToken();
    const { accessToken: actorToken } = await fixture.obtainActorToken();
    const actorSub = parseJwt(actorToken).payload['sub'];

    const exchanged = parseJwt(await fixture.exchangeToken(subjectToken, 'access_token', actorToken)).payload;

    expect(exchanged['act']).toMatchObject({ sub: actorSub });
    expect(exchanged).not.toHaveProperty('tx_actor_jti');
  });
});
