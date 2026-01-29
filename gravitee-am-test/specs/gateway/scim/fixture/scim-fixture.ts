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
import {
  createDomain,
  DomainOidcConfig,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { extractXsrfToken, performFormPost, performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { clearEmails, getLastEmail } from '@utils-commands/email-commands';

export interface ScimFixture {
  domain: Domain;
  accessToken: string;
  oidc: DomainOidcConfig;
  application: Application;
  scimAccessToken: string;
  scimEndpoint: string;
  cleanup: () => Promise<void>;

  clearMailbox: (email: string) => Promise<void>;
  extractConfirmRegistrationLink: (email: string) => Promise<string>;
  confirmRegistrationLink: (link: string) => Promise<any>;
}

export const setupFixture = async (): Promise<ScimFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('scim', true), 'Description');
  await startDomain(domain.id, accessToken);
  await patchDomain(domain.id, accessToken, {
    scim: {
      enabled: true,
      idpSelectionEnabled: false,
    },
  });

  const scimApp = await setupScimApp(domain.id, accessToken);
  const domainWithOidc = await startDomain(domain.id, accessToken).then((domain) => waitForDomainStart(domain));
  const scimAccessToken = await generateScimAccessToken(domainWithOidc.oidcConfig, scimApp);

  const scimEndpoint = process.env.AM_GATEWAY_URL + `/${domain.hrid}/scim`;
  return {
    domain: domain,
    accessToken: accessToken,
    oidc: domainWithOidc.oidcConfig,
    application: scimApp,
    scimEndpoint: scimEndpoint,
    scimAccessToken: scimAccessToken,

    clearMailbox: async (email: string) => {
      await clearEmails(email);
    },

    extractConfirmRegistrationLink: async (email: string) => {
      return (await getLastEmail(1000, email)).extractLink();
    },

    confirmRegistrationLink: async (link: string) => {
      return await confirmRegistration(link);
    },

    cleanup: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};

const setupScimApp = async (domainId: string, accessToken: string): Promise<Application> => {
  const createAppRequest: Application = {
    name: uniqueName('SCIM App', true),
    type: 'SERVICE',
    settings: {
      oauth: {
        grantTypes: ['client_credentials'],
        scopeSettings: [
          {
            scope: 'scim',
            defaultScope: true,
          },
        ],
      },
    },
  };
  const app = await createApplication(domainId, accessToken, createAppRequest);
  await updateApplication(domainId, accessToken, createAppRequest, app.id);
  return app;
};

const generateScimAccessToken = async (oidc: DomainOidcConfig, scimClient: Application): Promise<string> => {
  const response = await performPost(oidc.token_endpoint, '', 'grant_type=client_credentials', {
    'Content-type': 'application/x-www-form-urlencoded',
    Authorization: 'Basic ' + applicationBase64Token(scimClient),
  });
  return response.body.access_token;
};

const confirmRegistration = async (confirmationLink: string): Promise<any> => {
  // Confirm registration
  const url = new URL(confirmationLink);
  const resetPwdToken = url.searchParams.get('token');
  const baseUrlConfirmRegister = confirmationLink.substring(0, confirmationLink.indexOf('?'));

  const { headers, token: xsrfToken } = await extractXsrfToken(baseUrlConfirmRegister, '?token=' + resetPwdToken);

  return await performFormPost(
    baseUrlConfirmRegister,
    '',
    {
      'X-XSRF-TOKEN': xsrfToken,
      token: resetPwdToken,
      password: '#CoMpL3X-P@SsW0Rd',
    },
    {
      Cookie: headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);
};

export const createScimUserBody = (userName: string, givenName = 'Barbara', familyName = 'Jensen', externalId?: string) => ({
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
  "externalId": externalId || Math.floor(Math.random() * 1000000).toString(),
  "userName": userName,
  "name": {
    "formatted": `Ms. ${givenName} J ${familyName}, III`,
    "familyName": familyName,
    "givenName": givenName,
    "middleName": "Jane",
    "honorificPrefix": "Ms.",
    "honorificSuffix": "III"
  },
  "displayName": `${givenName} ${familyName}`,
  "nickName": givenName,
  "emails": [
    {
      "value": userName,
      "type": "work",
      "primary": true
    },
    {
      "value": "babs@jensen.org",
      "type": "home"
    }
  ],
  "userType": "Employee",
  "active": true
});

export const createScimGroupBody = (displayName: string) => ({
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:Group"],
  "displayName": displayName
});
