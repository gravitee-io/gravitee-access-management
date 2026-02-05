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
import { afterAll, beforeAll, expect } from '@jest/globals';
import { OIDCFixture, setupOidcProviderTest, TEST_USER } from './common';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: OIDCFixture;

beforeAll(async function () {
  fixture = await setupOidcProviderTest(uniqueName('response-mode', true));
});

function expectRedirectToClientWithAuthCode(res) {
  return (res) => fixture.expectRedirectToClient(res, (uri: string) => expect(uri).toMatch(/\?code=[^&]*/));
}

describe('The OIDC provider 2', () => {
  describe('When response_type = "code"', () => {
    const RESPONSE_TYPE = 'code';
    it('should use response_mode=query by default', async () => {
      await fixture.idpPluginInClient.setResponseType({ type: RESPONSE_TYPE, mode: 'default' });
      await fixture
        .login(TEST_USER.username, TEST_USER.password, { oidcSignInUrlAssertions: expectResponseModeParam(null) })
        .then(expectRedirectToClientWithAuthCode)
        .then((code) => expect(code).not.toBeNull());
    });
    it('should use response_mode=query when explicitly configured', async () => {
      await fixture.idpPluginInClient.setResponseType({ type: RESPONSE_TYPE, mode: 'query' });
      await fixture
        .login(TEST_USER.username, TEST_USER.password, { oidcSignInUrlAssertions: expectResponseModeParam(null) })
        .then(expectRedirectToClientWithAuthCode)
        .then((code) => expect(code).not.toBeNull());
    });
    it('should use response_mode=fragment when explicitly configured', async () => {
      await fixture.idpPluginInClient.setResponseType({ type: RESPONSE_TYPE, mode: 'fragment' });
      await fixture
        .login(TEST_USER.username, TEST_USER.password, {
          oidcSignInUrlAssertions: expectResponseModeParam('fragment'),
          oidcIdpFlow: 'implicit',
        })
        .then(expectRedirectToClientWithAuthCode)
        .then((code) => expect(code).not.toBeNull());
    });
    it('should use response_mode=form_post when explicitly configured', async () => {
      await fixture.idpPluginInClient.setResponseType({ type: RESPONSE_TYPE, mode: 'form_post' });
      await fixture
        .login(TEST_USER.username, TEST_USER.password, { oidcSignInUrlAssertions: expectResponseModeParam('form_post') })
        .then(expectRedirectToClientWithAuthCode)
        .then((code) => expect(code).not.toBeNull());
    });
  });
  describe('When response_type = "id_token"', () => {
    const RESPONSE_TYPE = 'id_token';
    it('should use response_mode=fragment by default', async () => {
      await fixture.idpPluginInClient.setResponseType({ type: RESPONSE_TYPE, mode: 'default' });
      await fixture
        .login(TEST_USER.username, TEST_USER.password, { oidcSignInUrlAssertions: expectResponseModeParam(null), oidcIdpFlow: 'implicit' })
        .then(expectRedirectToClientWithAuthCode)
        .then((code) => expect(code).not.toBeNull());
    });
    it('should use response_mode=query when explicitly configured', async () => {
      await fixture.idpPluginInClient.setResponseType({ type: RESPONSE_TYPE, mode: 'query' });
      await fixture
        .login(TEST_USER.username, TEST_USER.password, {
          oidcSignInUrlAssertions: expectResponseModeParam('query'),
          oidcLoginFailureAssertions: (res) => {
            // AM as OIDC provider doesn't support query response mode for response types that default to fragment mode,
            // but some weird providers might need this for compatibility
            expect(res.header['location']).toContain(
              'error=unsupported_response_mode&error_description=response_mode+%27query%27+is+incompatible+with+response_type+%27id_token%27',
            );
          },
          oidcIdpFlow: 'code',
        })
        .then(expectRedirectToClientWithAuthCode)
        .then((code) => expect(code).not.toBeNull());
    });
    it('should use response_mode=fragment when explicitly configured', async () => {
      await fixture.idpPluginInClient.setResponseType({ type: RESPONSE_TYPE, mode: 'fragment' });
      await fixture
        .login(TEST_USER.username, TEST_USER.password, { oidcSignInUrlAssertions: expectResponseModeParam(null), oidcIdpFlow: 'implicit' })
        .then(expectRedirectToClientWithAuthCode)
        .then((code) => expect(code).not.toBeNull());
    });
  });
});

afterAll(async () => {
  await fixture.cleanup();
});

function expectResponseModeParam(responseMode: string): (url: string) => void {
  if (responseMode && responseMode != '') {
    return (url) => expect(url).toContain('response_mode=' + responseMode);
  } else {
    return (url: string) => expect(url).not.toContain('response_mode=');
  }
}
