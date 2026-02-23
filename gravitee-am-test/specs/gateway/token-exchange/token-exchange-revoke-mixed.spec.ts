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
import { setupTokenExchangeFixture, TokenExchangeFixture } from './fixtures/token-exchange-fixture';

setup(200000);

let mixedFixture: TokenExchangeFixture;

beforeAll(async () => {
  mixedFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'token-exchange-revoke-mixed',
    domainDescription: 'Token exchange revoke mixed impersonation and delegation',
    clientName: 'token-exchange-revoke-mixed-client',
    allowImpersonation: true,
    allowDelegation: true,
  });
});

afterAll(async () => {
  if (mixedFixture) {
    await mixedFixture.cleanup();
  }
});

describe('Token Exchange revoke mixed', () => {
  it('should revoke mixed chain when root subject token is revoked', async () => {
    const { obtainSubjectToken, obtainActorToken, introspectToken, revokeToken, exchangeToken } = mixedFixture;

    const { accessToken: rootSubjectToken } = await obtainSubjectToken();
    const level1ImpersonationToken = await exchangeToken(rootSubjectToken, 'access_token');

    const { accessToken: actorToken } = await obtainActorToken();
    const level2DelegationToken = await exchangeToken(level1ImpersonationToken, 'access_token', actorToken);
    const level3ImpersonationToken = await exchangeToken(level2DelegationToken, 'access_token');

    expect((await introspectToken(rootSubjectToken)).active).toBe(true);
    expect((await introspectToken(level1ImpersonationToken)).active).toBe(true);
    expect((await introspectToken(level2DelegationToken)).active).toBe(true);
    expect((await introspectToken(level3ImpersonationToken)).active).toBe(true);
    expect((await introspectToken(actorToken)).active).toBe(true);

    await revokeToken(rootSubjectToken, 'access_token');

    expect((await introspectToken(rootSubjectToken)).active).toBe(false);
    expect((await introspectToken(level1ImpersonationToken)).active).toBe(false);
    expect((await introspectToken(level2DelegationToken)).active).toBe(false);
    expect((await introspectToken(level3ImpersonationToken)).active).toBe(false);
    expect((await introspectToken(actorToken)).active).toBe(true);
  });

  it('should revoke mixed chain when root actor token is revoked', async () => {
    const { obtainSubjectToken, obtainActorToken, introspectToken, revokeToken, exchangeToken } = mixedFixture;

    const { accessToken: rootSubjectToken } = await obtainSubjectToken();
    const { accessToken: rootActorToken } = await obtainActorToken();
    const level1DelegationToken = await exchangeToken(rootSubjectToken, 'access_token', rootActorToken);
    const level2ImpersonationToken = await exchangeToken(level1DelegationToken, 'access_token');

    const { accessToken: actor2Token } = await obtainActorToken();
    const level3DelegationToken = await exchangeToken(level2ImpersonationToken, 'access_token', actor2Token);

    expect((await introspectToken(rootSubjectToken)).active).toBe(true);
    expect((await introspectToken(rootActorToken)).active).toBe(true);
    expect((await introspectToken(level1DelegationToken)).active).toBe(true);
    expect((await introspectToken(level2ImpersonationToken)).active).toBe(true);
    expect((await introspectToken(level3DelegationToken)).active).toBe(true);
    expect((await introspectToken(actor2Token)).active).toBe(true);

    await revokeToken(rootActorToken, 'access_token');

    expect((await introspectToken(rootSubjectToken)).active).toBe(true);
    expect((await introspectToken(rootActorToken)).active).toBe(false);
    expect((await introspectToken(level1DelegationToken)).active).toBe(false);
    expect((await introspectToken(level2ImpersonationToken)).active).toBe(false);
    expect((await introspectToken(level3DelegationToken)).active).toBe(false);
    expect((await introspectToken(actor2Token)).active).toBe(true);
  });

  it('should keep outsider mixed chain active after revoking another mixed chain', async () => {
    const { obtainSubjectToken, obtainActorToken, introspectToken, revokeToken, exchangeToken } = mixedFixture;

    const { accessToken: subjectTokenA } = await obtainSubjectToken();
    const { accessToken: actorTokenA } = await obtainActorToken();
    const chainAToken1 = await exchangeToken(subjectTokenA, 'access_token', actorTokenA);
    const chainAToken2 = await exchangeToken(chainAToken1, 'access_token');

    const { accessToken: subjectTokenB } = await obtainSubjectToken();
    const { accessToken: actorTokenB } = await obtainActorToken();
    const chainBToken1 = await exchangeToken(subjectTokenB, 'access_token', actorTokenB);
    const chainBToken2 = await exchangeToken(chainBToken1, 'access_token');

    expect((await introspectToken(chainAToken2)).active).toBe(true);
    expect((await introspectToken(chainBToken2)).active).toBe(true);

    await revokeToken(actorTokenA, 'access_token');

    expect((await introspectToken(subjectTokenA)).active).toBe(true);
    expect((await introspectToken(actorTokenA)).active).toBe(false);
    expect((await introspectToken(chainAToken1)).active).toBe(false);
    expect((await introspectToken(chainAToken2)).active).toBe(false);

    expect((await introspectToken(subjectTokenB)).active).toBe(true);
    expect((await introspectToken(actorTokenB)).active).toBe(true);
    expect((await introspectToken(chainBToken1)).active).toBe(true);
    expect((await introspectToken(chainBToken2)).active).toBe(true);
  });
});
