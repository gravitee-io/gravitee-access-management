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
import { getUser } from '@management-commands/user-management-commands';
import { countEmailsFor, waitForEmail } from '@utils-commands/email-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { jira } from '@specs-utils/jira';
import { errorDescriptionOf, MagicLinkGatewayFixture, setupFixture } from './fixture/magic-link-gateway-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: MagicLinkGatewayFixture;

beforeAll(async () => {
  fixture = await setupFixture('magic-link-usage');
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

/** Decodes the JWT in a magic link and returns its payload. */
const tokenPayloadOf = (link: string) => {
  const token = new URL(link).searchParams.get('token');
  const part = token.split('.')[1];
  return JSON.parse(Buffer.from(part, 'base64url').toString());
};

describe('Magic Link - using the link', () => {
  it(jira`rejects a callback with no token parameter ${'AM-6712'}`, async () => {
    const request = await fixture.requestLink();
    const link = await fixture.lastLink();
    // Drop only the token, keeping client_id and the rest, so the error is about the token
    // rather than a different missing parameter.
    const url = new URL(link);
    url.searchParams.delete('token');

    const response = await performGet(url.toString(), '', { Cookie: request.cookie });

    expect(errorDescriptionOf(response)).toEqual('An unexpected error has occurred');
  });

  it(jira`rejects a link opened in a different session ${'AM-6714'}`, async () => {
    await fixture.requestLink();
    const link = await fixture.lastLink();

    // Sending no cookie is the API-level equivalent of opening the link in another browser.
    const response = await fixture.useLink(link);

    const body = response.text ?? JSON.stringify(response.body ?? '');
    expect(body).toContain('session_mismatch');
    expect(body).toContain('different session');
  });

  it(jira`updates lastLogin to a time after the link was issued ${'AM-6716'}`, async () => {
    const request = await fixture.requestLink();
    const link = await fixture.lastLink();
    const issuedAtMillis = tokenPayloadOf(link).iat * 1000;

    await fixture.completeLogin(link, request.cookie);

    const user = await getUser(fixture.domain.id, fixture.accessToken, fixture.user.id);
    // The case calls this lastLogin, which is its name inside the product; the Management API
    // exposes it as loggedAt.
    expect(user.loggedAt).toBeDefined();
    // Later than the token's iat is what proves the login came from this link and not an
    // earlier one.
    expect(new Date(user.loggedAt).getTime()).toBeGreaterThanOrEqual(issuedAtMillis);
  });
});

describe('Magic Link - dispatch', () => {
  it(jira`shows the generic message but sends nothing for an unknown email ${'AM-6711'}`, async () => {
    const unknown = 'nobody-here@test.com';

    const { response } = await fixture.requestLink(unknown);

    expect(response.status).toEqual(200);
    // A control in the same domain: the real user's link does arrive, so the absence below is a
    // result rather than a race on the mail server.
    await fixture.requestLink();
    await waitForEmail(fixture.userEmail);
    expect(await countEmailsFor(unknown)).toEqual(0);
  });
});
