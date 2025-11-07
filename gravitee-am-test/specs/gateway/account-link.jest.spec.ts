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
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { Domain } from '@management-models/Domain';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createIdp, getAllIdps } from '@management-commands/idp-management-commands';
import { createUser, deleteUser, getAllUsers } from '@management-commands/user-management-commands';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { delay } from '@utils-commands/misc';
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

// How this tests works:
// 1. Two domains - one acts as OIDC provider (domainOIDC), the second is for testing (domainAccLinking)
// 2. domainAccLinking has two apps - one with a hidden login form and OICD as a default idp to redirect directly to OIDC provider, the second is using the local idp provider (mongo/JDBC).

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();

  // Configure domainOIDC
  domainOIDC = await createDomain(accessToken, 'oidc-domain', 'OIDC provider domain.');
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
  await createApplication(domainOIDC.id, accessToken, {
    name: 'oidc',
    type: 'WEB',
    clientId: 'oidc',
    clientSecret: 'oidc',
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

  // Configure domainAccLinking
  domainAccLinking = await createDomain(accessToken, 'account-linking-domain', 'Account Linking test domain.');

  //Enable bruteforce
  await patchDomain(domainAccLinking.id, accessToken, {
    accountSettings: {
      maxLoginAttempts: 3,
      loginAttemptsDetectionEnabled: true,
      accountBlockedDuration: 180,
      loginAttemptsResetTime: 120,
    },
  });

  //Create oidc provider
  const idpConfig = {
    clientId: 'oidc',
    clientSecret: 'oidc',
    clientAuthenticationMethod: 'client_secret_basic',
    wellKnownUri: 'http://localhost:8092/oidc-domain/oidc/.well-known/openid-configuration',
    userAuthorizationUri: 'http://localhost:8092/oidc-domain/oauth/authorize',
    accessTokenUri: 'http://localhost:8092/oidc-domain/oauth/token',
    userProfileUri: 'http://localhost:8092/oidc-domain/oidc/userinfo',
    logoutUri: 'http://localhost:8092/oidc-domain/logout',
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
  let openIdIdp = {
    configuration: `${JSON.stringify(idpConfig)}`,
    name: 'oidc',
    type: 'oauth2-generic-am-idp',
    external: true,
  };
  oidcIdp = await createIdp(domainAccLinking.id, accessToken, openIdIdp);

  // Create an application with oidc provider
  applicationAccLinking = await createApplication(domainAccLinking.id, accessToken, {
    name: 'app',
    type: 'WEB',
    clientId: 'app',
    clientSecret: 'app',
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

  // Create an application with an internal provider
  const idpSetAccountLinkingDomain = await getAllIdps(domainAccLinking.id, accessToken);
  const localIdp = idpSetAccountLinkingDomain.filter((idp) => idp.type === 'mongo-am-idp' || 'jdbc-am-idp');
  localIdpApp = await createApplication(domainAccLinking.id, accessToken, {
    name: 'app-local',
    type: 'WEB',
    clientId: 'app-local',
    clientSecret: 'app-local',
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
  domainUser = getUser('domain-user');
  // Create the same user
  await createUser(domainAccLinking.id, accessToken, domainUser);

  //Start OIDC Domain
  const domainOIDCStarted = await startDomain(domainOIDC.id, accessToken).then(() => waitForDomainStart(domainOIDC));
  domainOIDC = domainOIDCStarted.domain;

  //Start Master Domain
  const masterDomainStarted = await startDomain(domainAccLinking.id, accessToken).then(() => waitForDomainStart(domainAccLinking));
  domainAccLinking = masterDomainStarted.domain;
  domainAccLinkingConfiguration = masterDomainStarted.oidcConfig;
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
    const authResponse = await performGet(
      domainAccLinkingConfiguration.authorization_endpoint,
      `?response_type=code&client_id=${localIdpApp.settings.oauth.clientId}&redirect_uri=https://test.com`,
      { Cookie: cookies },
    ).expect(302);
    expect(authResponse.headers['location']).toContain('test.com?code=');
  });

  it('Should execute brute force not allow to login with domain user', async () => {
    //Attempt fail login
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

    // Try to log in with OIDC
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

    //Try to log in with OIDC (user still locked)
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
  if (domainAccLinking && domainAccLinking.id) {
    await safeDeleteDomain(domainAccLinking.id, accessToken);
  }

  if (domainOIDC && domainOIDC.id) {
    await safeDeleteDomain(domainOIDC.id, accessToken);
  }
});
