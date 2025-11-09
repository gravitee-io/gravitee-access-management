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
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import {
  createDomain,
  safeDeleteDomain,
  getDomainFlows,
  patchDomain,
  startDomain,
  updateDomainFlows,
  waitForDomainStart,
  waitForDomainSync,
  waitForApplicationSync,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { Domain } from '@management-models/Domain';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createIdp, getAllIdps } from '@management-commands/idp-management-commands';
import { createUser, deleteUser, getAllUsers } from '@management-commands/user-management-commands';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { lookupFlowAndResetPolicies } from '@management-commands/flow-management-commands';

jest.setTimeout(200000);

let accessToken: string;
let domainAccLinking: Domain;
let domainOIDC: Domain;
let domainAccLinkingConfiguration: any;
let applicationAccLinking: any;
let oidcUser: any;
let domainUser: any;
let cookies: any;
let localIdpApp: any;
let oidcIdp: any;

// How this test works:
// 1. Two domains - one acts as OIDC provider (domainOIDC), the second is for testing (domainAccLinking)
// 2. domainAccLinking has two apps - one with a hidden login form and OIDC as a default idp to redirect directly to OIDC provider,
//    the second is using the local idp provider (mongo/JDBC).

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();

  // Configure domainOIDC
  domainOIDC = await createDomain(accessToken, uniqueName('oidc-domain', true), 'OIDC provider domain.');
  const idpSet = await getAllIdps(domainOIDC.id, accessToken);
  await patchDomain(domainOIDC.id, accessToken, {
    oidc: {
      clientRegistrationSettings: {
        allowLocalhostRedirectUri: true,
        allowHttpSchemeRedirectUri: true,
      },
    },
  });

  // Create an oidc application with internal IDP
  const oidcClientId = uniqueName('oidc', true);
  const oidcClientSecret = uniqueName('oidc', true);
  const oidcApp = await createApplication(domainOIDC.id, accessToken, {
    name: oidcClientId,
    type: 'WEB',
    clientId: oidcClientId,
    clientSecret: oidcClientSecret,
    redirectUris: ['http://localhost:8092/account-linking-domain/login/callback'],
  }).then((app) =>
    updateApplication(
      domainOIDC.id,
      accessToken,
      {
        settings: {
          oauth: {
            scopeSettings: [
              {
                scope: 'openid',
                defaultScope: true,
              },
            ],
          },
        },
        identityProviders: [{ identity: idpSet.values().next().value.id, priority: -1 }],
      },
      app.id,
    ),
  );
  // Create user in oidc
  oidcUser = getUser('oidc');
  await createUser(domainOIDC.id, accessToken, oidcUser);
  await waitForDomainSync(domainOIDC.id, accessToken);

  // Wait for OIDC application to be synced to gateway before starting domain
  await waitForApplicationSync(domainOIDC.id, accessToken, oidcApp.id);

  // Configure domainAccLinking
  domainAccLinking = await createDomain(accessToken, uniqueName('account-linking-domain', true), 'Account Linking test domain.');
  // Update OIDC application redirectUri with actual domainAccLinking hrid
  await updateApplication(
    domainOIDC.id,
    accessToken,
    {
      settings: {
        oauth: {
          redirectUris: [`http://localhost:8092/${domainAccLinking.hrid}/login/callback`],
        },
      },
    },
    oidcApp.id,
  );
  // Wait for OIDC application to be synced to gateway
  await waitForApplicationSync(domainOIDC.id, accessToken, oidcApp.id);

  // Enable brute force protection
  await patchDomain(domainAccLinking.id, accessToken, {
    accountSettings: {
      maxLoginAttempts: 3,
      loginAttemptsDetectionEnabled: true,
      accountBlockedDuration: 180,
      loginAttemptsResetTime: 120,
    },
  });
  await waitForDomainSync(domainAccLinking.id, accessToken);

  // Create OIDC provider
  const idpConfig = {
    clientId: oidcClientId,
    clientSecret: oidcClientSecret,
    clientAuthenticationMethod: 'client_secret_basic',
    wellKnownUri: `http://localhost:8092/${domainOIDC.hrid}/oidc/.well-known/openid-configuration`,
    userAuthorizationUri: `http://localhost:8092/${domainOIDC.hrid}/oauth/authorize`,
    accessTokenUri: `http://localhost:8092/${domainOIDC.hrid}/oauth/token`,
    userProfileUri: `http://localhost:8092/${domainOIDC.hrid}/oidc/userinfo`,
    logoutUri: `http://localhost:8092/${domainOIDC.hrid}/logout`,
    responseType: 'code',
    responseMode: 'default',
    encodeRedirectUri: false,
    useIdTokenForUserInfo: false,
    signature: 'RSA_RS256',
    publicKeyResolver: 'GIVEN_KEY',
    connectTimeout: 10000,
    idleTimeout: 10000,
    maxPoolSize: 200,
    storeOriginalTokens: false,
  };
  const openIdIdp = {
    configuration: `${JSON.stringify(idpConfig)}`,
    name: 'oidc',
    type: 'oauth2-generic-am-idp',
    external: true,
  };
  oidcIdp = await createIdp(domainAccLinking.id, accessToken, openIdIdp);
  // Wait for OIDC IDP to be synced to gateway
  await waitForDomainSync(domainAccLinking.id, accessToken);

  // Create an application with oidc provider
  const appClientId = uniqueName('app', true);
  const appClientSecret = uniqueName('app', true);
  applicationAccLinking = await createApplication(domainAccLinking.id, accessToken, {
    name: appClientId,
    type: 'WEB',
    clientId: appClientId,
    clientSecret: appClientSecret,
    redirectUris: ['https://test.com'],
  }).then((app) =>
    updateApplication(
      domainAccLinking.id,
      accessToken,
      {
        identityProviders: [{ identity: oidcIdp.id, priority: -1 }],
        settings: {
          login: {
            inherited: false,
            hideForm: true,
          },
        },
      },
      app.id,
    ),
  );
  // Wait for application to be synced to gateway
  await waitForApplicationSync(domainAccLinking.id, accessToken, applicationAccLinking.id);

  // Create an application with an internal provider
  const idpSetAccountLinkingDomain = await getAllIdps(domainAccLinking.id, accessToken);
  const localIdp = idpSetAccountLinkingDomain.filter((idp) => idp.type === 'mongo-am-idp' || idp.type === 'jdbc-am-idp');
  const appLocalClientId = uniqueName('app-local', true);
  const appLocalClientSecret = uniqueName('app-local', true);
  localIdpApp = await createApplication(domainAccLinking.id, accessToken, {
    name: appLocalClientId,
    type: 'WEB',
    clientId: appLocalClientId,
    clientSecret: appLocalClientSecret,
    redirectUris: ['https://test.com'],
  }).then((app) =>
    updateApplication(
      domainAccLinking.id,
      accessToken,
      {
        identityProviders: [{ identity: localIdp.values().next().value.id, priority: -1 }],
      },
      app.id,
    ),
  );
  // Wait for application to be synced to gateway
  await waitForApplicationSync(domainAccLinking.id, accessToken, localIdpApp.id);

  domainUser = getUser('domain-user');
  // Create the same user
  await createUser(domainAccLinking.id, accessToken, domainUser);
  await waitForDomainSync(domainAccLinking.id, accessToken);

  // Start OIDC Domain
  const domainOIDCStarted = await startDomain(domainOIDC.id, accessToken).then(() => waitForDomainStart(domainOIDC));
  domainOIDC = domainOIDCStarted.domain;
  // Wait for OIDC application to be synced after domain start
  await waitForApplicationSync(domainOIDC.id, accessToken, oidcApp.id);
  await waitForDomainSync(domainOIDC.id, accessToken);

  // Start Master Domain
  const masterDomainStarted = await startDomain(domainAccLinking.id, accessToken).then(() => waitForDomainStart(domainAccLinking));
  domainAccLinking = masterDomainStarted.domain;
  domainAccLinkingConfiguration = masterDomainStarted.oidcConfig;
  // Wait for applications to be synced after domain start
  await waitForApplicationSync(domainAccLinking.id, accessToken, applicationAccLinking.id);
  await waitForApplicationSync(domainAccLinking.id, accessToken, localIdpApp.id);
  await waitForDomainSync(domainAccLinking.id, accessToken);
});

describe('Account Linking - local IDP and OIDC', () => {
  it('Should login and create double user - account linking not setup', async () => {
    const user1TokenResponse = await loginUserNameAndPassword(
      applicationAccLinking.settings.oauth.clientId,
      oidcUser,
      'Test1234567!',
      false,
      domainAccLinkingConfiguration,
      domainAccLinking,
      'https://test.com',
      'code',
      true,
    );
    expect(user1TokenResponse.headers['location']).toContain('test.com?code=');
    const allUsers = await getAllUsers(domainAccLinking.id, accessToken);
    expect(allUsers.data.length).toBe(2);
    // Remove and recreate users
    await deleteUser(domainAccLinking.id, accessToken, allUsers.data[0].id);
    await deleteUser(domainAccLinking.id, accessToken, allUsers.data[1].id);
    await createUser(domainAccLinking.id, accessToken, domainUser);
  });

  it('Should login and create double user - account linking setup', async () => {
    const flows = await getDomainFlows(domainAccLinking.id, accessToken);
    lookupFlowAndResetPolicies(flows, 'CONNECT', 'pre', [
      {
        name: 'Account Linking',
        policy: 'policy-am-account-linking',
        description: '',
        configuration: JSON.stringify({
          exitIfNoAccount: false,
          exitIfMultipleAccount: false,
          userAttributes: [{ name: 'email', value: 'test@test.fr' }],
        }),
        enabled: true,
        condition: '',
      },
    ]);
    await updateDomainFlows(domainAccLinking.id, accessToken, flows);
    await waitForDomainSync(domainAccLinking.id, accessToken);

    const user1TokenResponse = await loginUserNameAndPassword(
      applicationAccLinking.settings.oauth.clientId,
      oidcUser,
      'Test1234567!',
      false,
      domainAccLinkingConfiguration,
      domainAccLinking,
      'https://test.com',
      'code',
      true,
    );
    expect(user1TokenResponse.headers['location']).toContain('test.com?code=');
    cookies = user1TokenResponse.headers['set-cookie'];
    const allUsers = await getAllUsers(domainAccLinking.id, accessToken);
    expect(allUsers.data.length).toBe(1);
    expect(allUsers.data[0].identities[0].providerId).toBe(oidcIdp.id);
  });

  it('Should login to second app with local idp', async () => {
    // Ensure cookies are available from previous test
    if (!cookies) {
      // If cookies are not available, we need to login first
      const loginResponse = await loginUserNameAndPassword(
        applicationAccLinking.settings.oauth.clientId,
        oidcUser,
        'Test1234567!',
        false,
        domainAccLinkingConfiguration,
        domainAccLinking,
        'https://test.com',
        'code',
        true,
      );
      cookies = loginResponse.headers['set-cookie'];
    }

    const authResponse = await performGet(
      domainAccLinkingConfiguration.authorization_endpoint,
      `?response_type=code&client_id=${localIdpApp.settings.oauth.clientId}&redirect_uri=https://test.com`,
      { Cookie: cookies },
    ).expect(302);
    expect(authResponse.headers['location']).toContain('test.com?code=');
  });

  it('Should execute brute force not allow to login with domain user', async () => {
    // Attempt failed logins
    for (let i = 0; i < 5; i++) {
      const failResponse = await loginUserNameAndPassword(
        localIdpApp.settings.oauth.clientId,
        domainUser,
        'Wrong123456Password!',
        false,
        domainAccLinkingConfiguration,
        domainAccLinking,
        'https://test.com',
        'code',
        false,
      );
      expect(failResponse.headers['location']).toContain('error=login_failed');
    }

    // Check that the user is locked
    const allUsers = await getAllUsers(domainAccLinking.id, accessToken);
    const lockedUser = allUsers.data.find((u) => u.username === domainUser.username);
    expect(lockedUser.accountNonLocked).toBe(false);

    // Try to log in with OIDC (should succeed)
    const user1TokenResponse = await loginUserNameAndPassword(
      applicationAccLinking.settings.oauth.clientId,
      oidcUser,
      'Test1234567!',
      false,
      domainAccLinkingConfiguration,
      domainAccLinking,
      'https://test.com',
      'code',
      true,
    );
    expect(user1TokenResponse.headers['location']).toContain('test.com?code=');

    // Try to log in with local IDP (user still locked)
    const lockedAttempt = await loginUserNameAndPassword(
      localIdpApp.settings.oauth.clientId,
      domainUser,
      'Test1234567!',
      false,
      domainAccLinkingConfiguration,
      domainAccLinking,
      'https://test.com',
      'code',
      false,
    );
    expect(lockedAttempt.headers['location']).toContain('error=login_failed');
  });
});

function getUser(username: string) {
  return {
    username: username,
    password: 'Test1234567!',
    firstName: username,
    lastName: username,
    email: 'test@test.fr',
  };
}

afterAll(async () => {
  if (domainAccLinking?.id) {
    await safeDeleteDomain(domainAccLinking.id, accessToken);
  }

  if (domainOIDC?.id) {
    await safeDeleteDomain(domainOIDC.id, accessToken);
  }
});
