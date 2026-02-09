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
import { beforeAll, expect } from '@jest/globals';
import { BasicResponse, uniqueName } from '@utils-commands/misc';
import { OIDCFixture, setupOidcProviderTest, TEST_USER } from './common';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: OIDCFixture;

beforeAll(async function () {
  fixture = await setupOidcProviderTest(uniqueName('pkce', true));
});

function expectRedirectToClientWithAuthCode(res) {
  return (res) => fixture.expectRedirectToClient(res, (uri: string) => expect(uri).toMatch(/\?code=[^&]*/));
}

describe('The OIDC provider', () => {
  describe("When provider doesn't force PKCE", () => {
    beforeAll(async () => {
      await fixture.providerApp.setPkceConfig({ forcePKCE: false, forceS256CodeChallengeMethod: false });
    });
    it('should login without PKCE', async () => {
      await fixture.idpPluginInClient.setPkceMethod(null);
      await fixture
        .login(TEST_USER.username, TEST_USER.password, { oidcSignInUrlAssertions: expectNoCodeChallenge })
        .then(expectRedirectToClientWithAuthCode)
        .then((code) => expect(code).not.toBeNull());
    });
    it('should login with plain challenge', async () => {
      await fixture.idpPluginInClient.setPkceMethod('plain');
      await fixture
        .login(TEST_USER.username, TEST_USER.password, { oidcSignInUrlAssertions: expectCodeChallenge('plain') })
        .then(expectRedirectToClientWithAuthCode)
        .then((code) => expect(code).not.toBeNull());
    });
    it('should login with S256 challenge', async () => {
      await fixture.idpPluginInClient.setPkceMethod('S256');
      await fixture
        .login(TEST_USER.username, TEST_USER.password, { oidcSignInUrlAssertions: expectCodeChallenge('S256') })
        .then(expectRedirectToClientWithAuthCode)
        .then((code) => expect(code).not.toBeNull());
    });
    it('should fail with challenge unsupported by provider', async () => {
      await expect(fixture.idpPluginInClient.setPkceMethod('non-existing-method')).rejects.toMatchObject({
        response: { status: 400 },
      });
    });
  });

  describe('When provider forces PKCE', () => {
    beforeAll(async () => {
      await fixture.providerApp.setPkceConfig({ forcePKCE: true, forceS256CodeChallengeMethod: false });
    });
    it('should fail login without PKCE', async () => {
      await fixture.idpPluginInClient.setPkceMethod(null);
      await fixture.login(TEST_USER.username, TEST_USER.password, {
        oidcSignInUrlAssertions: expectNoCodeChallenge,
        oidcLoginFailureAssertions: expectErrorRedirect('Missing+parameter%3A+code_challenge'),
      });
    });
    it('should login with plain challenge', async () => {
      await fixture.idpPluginInClient.setPkceMethod('plain');
      await fixture
        .login(TEST_USER.username, TEST_USER.password, { oidcSignInUrlAssertions: expectCodeChallenge('plain') })
        .then(expectRedirectToClientWithAuthCode)
        .then((code) => expect(code).not.toBeNull());
    });
    it('should login with S256 challenge', async () => {
      await fixture.idpPluginInClient.setPkceMethod('S256');
      await fixture
        .login(TEST_USER.username, TEST_USER.password, { oidcSignInUrlAssertions: expectCodeChallenge('S256') })
        .then(expectRedirectToClientWithAuthCode)
        .then((code) => expect(code).not.toBeNull());
    });
  });

  describe('When provider forces S256 method', () => {
    beforeAll(async () => {
      await fixture.providerApp.setPkceConfig({ forcePKCE: false, forceS256CodeChallengeMethod: true });
    });
    it('should login without PKCE', async () => {
      await fixture.idpPluginInClient.setPkceMethod(null);
      await fixture
        .login(TEST_USER.username, TEST_USER.password, { oidcSignInUrlAssertions: expectNoCodeChallenge })
        .then(expectRedirectToClientWithAuthCode)
        .then((code) => expect(code).not.toBeNull());
    });
    it('should fail login with plain challenge', async () => {
      await fixture.idpPluginInClient.setPkceMethod('plain');
      await fixture.login('user', '#CoMpL3X-P@SsW0Rd', {
        oidcSignInUrlAssertions: expectCodeChallenge('plain'),
        oidcLoginFailureAssertions: expectErrorRedirect('Invalid+parameter%3A+code_challenge_method'),
      });
    });
    it('should login with S256 challenge', async () => {
      await fixture.idpPluginInClient.setPkceMethod('S256');
      await fixture
        .login(TEST_USER.username, TEST_USER.password, { oidcSignInUrlAssertions: expectCodeChallenge('S256') })
        .then(expectRedirectToClientWithAuthCode)
        .then((code) => expect(code).not.toBeNull());
    });
  });

  describe('When provider forces PKCE with S256 method', () => {
    beforeAll(async () => {
      await fixture.providerApp.setPkceConfig({ forcePKCE: true, forceS256CodeChallengeMethod: true });
    });
    it('should fail login without PKCE', async () => {
      await fixture.idpPluginInClient.setPkceMethod(null);
      await fixture.login(TEST_USER.username, TEST_USER.password, {
        oidcSignInUrlAssertions: expectNoCodeChallenge,
        oidcLoginFailureAssertions: expectErrorRedirect('Missing+parameter%3A+code_challenge'),
      });
    });
    it('should fail login with plain challenge', async () => {
      await fixture.idpPluginInClient.setPkceMethod('plain');
      await fixture.login('user', '#CoMpL3X-P@SsW0Rd', {
        oidcSignInUrlAssertions: expectCodeChallenge('plain'),
        oidcLoginFailureAssertions: expectErrorRedirect('Invalid+parameter%3A+code_challenge_method'),
      });
    });
    it('should login with S256 challenge', async () => {
      await fixture.idpPluginInClient.setPkceMethod('S256');
      await fixture
        .login(TEST_USER.username, TEST_USER.password, { oidcSignInUrlAssertions: expectCodeChallenge('S256') })
        .then(expectRedirectToClientWithAuthCode)
        .then((code) => expect(code).not.toBeNull());
    });
  });
});

function expectNoCodeChallenge(url: string) {
  expect(url).not.toContain('code_challenge_method');
  expect(url).not.toContain('code_challenge');
}

function expectCodeChallenge(challengeMethod: string) {
  return (url: string) => {
    expect(url).toMatch(new RegExp(/[&?]code_challenge_method=/.source + challengeMethod));
    expect(url).toMatch(/([&?])code_challenge=[^&]+&/);
  };
}

function expectErrorRedirect(description: string, error: string = 'invalid_request') {
  return (response: BasicResponse) => {
    expect(response.status).toBe(302);
    const location = response.header['location'];
    expect(location).toContain('error=' + error);
    expect(location).toContain('error_description=' + description);
  };
}
