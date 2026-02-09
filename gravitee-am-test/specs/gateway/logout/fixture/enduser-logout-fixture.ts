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

import { expect } from '@jest/globals';
import { Domain } from '@management-models/Domain';
import { DomainOidcConfig } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, startDomain, waitForDomainStart } from '@management-commands/domain-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { extractXsrfTokenAndActionResponse, performFormPost, performGet } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

export interface EnduserLogoutFixture extends Fixture {
  domain: Domain;
  application: any;
  user: any;
  openIdConfiguration: DomainOidcConfig;
  signInUser: () => Promise<any>;
}

export const setupFixture = async (): Promise<EnduserLogoutFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('enduser-logout', true), 'test end-user logout');

  // Create all resources BEFORE starting domain so initial sync picks up everything
  const idpSet = await getAllIdps(domain.id, accessToken);
  const appClientId = uniqueName('app-logout', true);
  const appClientSecret = uniqueName('app-logout', true);
  const appName = uniqueName('my-client', true);
  const application = await createApplication(domain.id, accessToken, {
    name: appName,
    type: 'WEB',
    clientId: appClientId,
    clientSecret: appClientSecret,
    redirectUris: ['https://callback'],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['https://callback'],
            grantTypes: ['authorization_code'],
          },
        },
        identityProviders: [{ identity: idpSet.values().next().value.id, priority: -1 }],
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  const user = {
    username: uniqueName('LogoutUser', true),
    password: 'SomeP@ssw0rd',
    firstName: 'Logout',
    lastName: 'User',
    email: 'logoutuser@acme.fr',
    preRegistration: false,
  };
  await createUser(domain.id, accessToken, user);

  // Start domain — initial sync picks up app + user
  await startDomain(domain.id, accessToken);
  const domainStarted = await waitForDomainStart(domain);

  const openIdConfiguration = domainStarted.oidcConfig;

  const signInUser = async () => {
    const clientId = application.settings.oauth.clientId;
    const params = `?response_type=code&client_id=${clientId}&redirect_uri=https://callback`;

    // Initiate the Login Flow
    const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);
    const loginLocation = authResponse.headers['location'];

    expect(loginLocation).toBeDefined();
    expect(loginLocation).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/login`);
    expect(loginLocation).toContain(`client_id=${clientId}`);

    // Redirect to /login
    const loginResult = await extractXsrfTokenAndActionResponse(authResponse);
    // Authentication
    const postLogin = await performFormPost(
      loginResult.action,
      '',
      {
        'X-XSRF-TOKEN': loginResult.token,
        username: user.username,
        password: user.password,
        client_id: clientId,
      },
      {
        Cookie: loginResult.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    // Post authentication
    const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);

    expect(postLoginRedirect.headers['location']).toBeDefined();
    expect(postLoginRedirect.headers['location']).toContain(`https://callback`);
    expect(postLoginRedirect.headers['location']).toMatch(/code=[-_a-zA-Z0-9]+&?/);
    return postLoginRedirect;
  };

  return {
    accessToken,
    domain,
    application,
    user,
    openIdConfiguration,
    signInUser,
    cleanUp: async () => {
      if (domain?.id) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};
