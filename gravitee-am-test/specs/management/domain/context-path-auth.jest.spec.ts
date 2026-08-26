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
import { introspectToken, performGet, performPost, requestToken, signInUser } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { setup } from '../../test-fixture';
import { ContextPathAuthFixture, setupContextPathAuthFixture } from './fixtures/context-path-auth-fixture';

setup(300000);

/** Decode a JWT payload without verifying it — enough to read the issuer. */
const decodeJwtPayload = (token: string): Record<string, any> => {
  const segments = token.split('.');
  expect(segments).toHaveLength(3);
  return JSON.parse(Buffer.from(segments[1], 'base64url').toString('utf8'));
};

/**
 * AM-2224 / UC-AM17 — authenticating through an application after a domain's context path changes.
 *
 * `path-mode.jest.spec.ts` already covers validating the new path and confirming the discovery
 * document advertises endpoints under it. Advertising an endpoint and being able to use it are
 * different things, and this suite covers the second: a real sign-in against the moved path, the
 * issuer on the resulting token, and the old path no longer being served.
 */
describe('Domain context path — authenticating through an application (AM-2224)', () => {
  let fixture: ContextPathAuthFixture;

  beforeAll(async () => {
    fixture = await setupContextPathAuthFixture();
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  it(jira`a user signs in through an application on the new context path ${'AM-2224'}`, async () => {
    // signInUser drives the authorization endpoint from the discovery document, which now points
    // at the new path, and asserts the login redirect lands under it.
    const postLogin = await signInUser(fixture.domainOnNewPath, fixture.application, fixture.user, fixture.openIdConfiguration);

    const tokenResponse = await requestToken(fixture.application, fixture.openIdConfiguration, postLogin);
    expect(tokenResponse.body.access_token).toBeDefined();
    expect(tokenResponse.body.token_type).toEqual('bearer');
  });

  it(jira`the token issued from the new context path carries the matching issuer ${'AM-2224'}`, async () => {
    const postLogin = await signInUser(fixture.domainOnNewPath, fixture.application, fixture.user, fixture.openIdConfiguration);
    const tokenResponse = await requestToken(fixture.application, fixture.openIdConfiguration, postLogin);

    const issuer = decodeJwtPayload(tokenResponse.body.access_token).iss;
    expect(issuer).toContain(fixture.newPath);
    // The domain is no longer served on its original path, so a token still issued under it would
    // mean the change had only been applied to the advertised metadata.
    expect(issuer).not.toContain(`${fixture.originalPath}/oidc`);
  });

  it(jira`the previous context path is no longer served ${'AM-2224'}`, async () => {
    // The domain was reachable there before the change — otherwise a refusal below would prove
    // nothing, since an unrouted path is refused whether or not anything ever moved.
    expect(fixture.originalPathStatusBeforeChange).toEqual(200);

    const response = await performGet(process.env.AM_GATEWAY_URL, `${fixture.originalPath}/oidc/.well-known/openid-configuration`);

    expect(response.status).toBeGreaterThanOrEqual(400);
    expect(response.status).toBeLessThan(500);
  });
  it(jira`a token minted before the change stays valid but keeps the old issuer ${'AM-2224'}`, async () => {
    const introspection = await introspectToken(
      fixture.openIdConfiguration.introspection_endpoint,
      fixture.tokenMintedOnOriginalPath,
      applicationBase64Token(fixture.application),
    );

    // Moving a domain's context path is a routing change, not a revocation event, so a token
    // issued beforehand is still accepted at the endpoints in their new location.
    expect(introspection.active).toEqual(true);
    // It keeps the issuer it was minted with, which no longer resolves. Worth pinning down:
    // a relying party that validates iss, or runs discovery from it, will not find that path.
    expect(introspection.iss).toContain(fixture.originalPath);

    // The introspection endpoint on the old path is itself gone.
    const onOldPath = await performPost(
      process.env.AM_GATEWAY_URL,
      `${fixture.originalPath}/oauth/introspect`,
      `token=${fixture.tokenMintedOnOriginalPath}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${applicationBase64Token(fixture.application)}`,
      },
    );
    expect(onOldPath.status).toEqual(404);
  });
});
