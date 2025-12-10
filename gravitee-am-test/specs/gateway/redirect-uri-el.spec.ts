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
import fetch from 'cross-fetch';
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, patchDomain, startDomain, waitForDomainStart } from '@management-commands/domain-management-commands';
import { Domain } from '@management-models/Domain';
import { createJdbcIdp, createMongoIdp } from '@utils-commands/idps-commands';
import { createUser } from '@management-commands/user-management-commands';
import faker from 'faker';
import { createApplication, patchApplication } from '@management-commands/application-management-commands';
import { initiateLoginFlow, login } from '@gateway-commands/login-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';

global.fetch = fetch;

let domain: Domain;
let accessToken: string;
let customIdp: any;
let applicationFailingELParam: any;
let applicationSingleELParam: any;
let applicationMultiELParam: any;
let applicationMultiELParamAndRegular: any;
let applicationWithoutEL: any;

let oidc: any;
let user: any;

jest.setTimeout(200000);

const jdbc = process.env.REPOSITORY_TYPE;

const failingELParam = {
  callback1: 'https://callback/',
  callback2: 'https://callback2/',

  redirect_uri_1: `https://callback/?param={#context.attributes['nonexisting'].applicationType}`,
  redirect_uri_2: `https://callback2/?param={#noExistingProperty}&param3=test&param2={#context.attributes['client'].applicationType}`,
};

const normalConfiguration = {
  callback1: 'https://callback/',
  redirect_uri_1: `https://callback/?param=test`,
};

const singleParam = {
  callback1: 'https://callback/',
  callback2: 'https://callback2/',
  redirect_uri_1: `https://callback/?param={#context.attributes['client'].applicationType}`,
  redirect_uri_2: `https://callback2/?{#context.attributes['client'].applicationType}`,
};

const multiParam = {
  callback1: 'https://callback/',
  redirect_uri_1: `https://callback/?param={#context.attributes['client'].applicationType}&param2={#context.attributes['client'].applicationType}`,
};

const multiParamAndRegular = {
  callback1: 'https://callback/',
  redirect_uri_1: `https://callback/?param={#context.attributes['client'].applicationType}&param3=test&param2={#context.attributes['client'].applicationType}`,
  redirect_uri_with_el_another: `https://callback2/?param={#context.attributes['client'].applicationType}&param3=test&param2={#context.attributes['client'].applicationType}`,
};

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  domain = await createDomain(accessToken, uniqueName('redirect-uri', true), 'redirect uri with EL').then((d) =>
    patchDomain(d.id, accessToken, {
      oidc: {
        clientRegistrationSettings: {
          allowRedirectUriParamsExpressionLanguage: true,
        },
      },
    }),
  );
  customIdp = jdbc === 'jdbc' ? await createJdbcIdp(domain.id, accessToken) : await createMongoIdp(domain.id, accessToken);

  applicationSingleELParam = await createApplication(domain.id, accessToken, {
    name: faker.commerce.productName(),
    type: 'WEB',
    description: faker.lorem.paragraph(),
    redirectUris: [singleParam.redirect_uri_1, singleParam.redirect_uri_2],
  });

  await patchApplication(
    domain.id,
    accessToken,
    {
      identityProviders: new Set([{ identity: customIdp.id, priority: 0 }]),
    },
    applicationSingleELParam.id,
  );

  applicationMultiELParam = await createApplication(domain.id, accessToken, {
    name: faker.commerce.productName(),
    type: 'WEB',
    description: faker.lorem.paragraph(),
    redirectUris: [multiParam.redirect_uri_1],
  });

  await patchApplication(
    domain.id,
    accessToken,
    {
      identityProviders: new Set([{ identity: customIdp.id, priority: 0 }]),
    },
    applicationMultiELParam.id,
  );

  applicationMultiELParamAndRegular = await createApplication(domain.id, accessToken, {
    name: faker.commerce.productName(),
    type: 'WEB',
    description: faker.lorem.paragraph(),
    redirectUris: [multiParamAndRegular.redirect_uri_1, multiParamAndRegular.redirect_uri_with_el_another],
  });

  await patchApplication(
    domain.id,
    accessToken,
    {
      identityProviders: new Set([{ identity: customIdp.id, priority: 0 }]),
    },
    applicationMultiELParamAndRegular.id,
  );

  applicationFailingELParam = await createApplication(domain.id, accessToken, {
    name: faker.commerce.productName(),
    type: 'WEB',
    description: faker.lorem.paragraph(),
    redirectUris: [failingELParam.redirect_uri_1, failingELParam.redirect_uri_2],
  });

  await patchApplication(
    domain.id,
    accessToken,
    {
      identityProviders: new Set([{ identity: customIdp.id, priority: 0 }]),
    },
    applicationFailingELParam.id,
  );

  applicationWithoutEL = await createApplication(domain.id, accessToken, {
    name: faker.commerce.productName(),
    type: 'WEB',
    description: faker.lorem.paragraph(),
    redirectUris: [normalConfiguration.redirect_uri_1],
  });

  await patchApplication(
    domain.id,
    accessToken,
    {
      identityProviders: new Set([{ identity: customIdp.id, priority: 0 }]),
    },
    applicationWithoutEL.id,
  );

  user = await createUser(domain.id, accessToken, {
    firstName: 'john',
    lastName: 'doe',
    email: 'john.doe@test.com',
    username: 'john.doe',
    password: 'Password123!',
    source: customIdp.id,
    preRegistration: false,
  });
  user.password = 'Password123!';

  await startDomain(domain.id, accessToken)
    .then(() => waitForDomainStart(domain))
    .then((startedDomain) => {
      domain = startedDomain.domain;
      oidc = startedDomain.oidcConfig;
    });
});

describe('Redirect URI', () => {
  describe('Dynamic parameters', () => {
    it('No EL redirect_uri registered', async () => {
      const authResponse = await initiateLoginFlow(
        applicationWithoutEL.settings.oauth.clientId,
        oidc,
        domain,
        'code',
        singleParam.callback1,
      );
      const loginResponse = await login(authResponse, user.username, applicationWithoutEL.settings.oauth.clientId, user.password);
      const tokenResponse = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse.headers['location']).toContain('https://callback/?param=test');
      expect(tokenResponse.headers['location']).toContain('code=');
    });

    it('Single dynamic parameter without key', async () => {
      const authResponse = await initiateLoginFlow(
        applicationSingleELParam.settings.oauth.clientId,
        oidc,
        domain,
        'code',
        singleParam.callback2,
      );
      const loginResponse = await login(authResponse, user.username, applicationSingleELParam.settings.oauth.clientId, user.password);
      const tokenResponse = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse.headers['location']).toContain('https://callback2/?web');
      expect(tokenResponse.headers['location']).toContain('code=');
    });

    it('Single dynamic parameter with key', async () => {
      const authResponse = await initiateLoginFlow(
        applicationSingleELParam.settings.oauth.clientId,
        oidc,
        domain,
        'code',
        singleParam.callback1,
      );
      const loginResponse = await login(authResponse, user.username, applicationSingleELParam.settings.oauth.clientId, user.password);
      const tokenResponse = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse.headers['location']).toContain('https://callback/?param=web');
      expect(tokenResponse.headers['location']).toContain('code=');
    });

    it('Multi EL parameters', async () => {
      const authResponse = await initiateLoginFlow(
        applicationMultiELParam.settings.oauth.clientId,
        oidc,
        domain,
        'code',
        multiParam.callback1,
      );
      const loginResponse = await login(authResponse, user.username, applicationMultiELParam.settings.oauth.clientId, user.password);
      const tokenResponse = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse.headers['location']).toContain('https://callback/?param=web&param2=web');
      expect(tokenResponse.headers['location']).toContain('code=');
    });

    it('Multi EL parameters with regular one', async () => {
      const authResponse = await initiateLoginFlow(
        applicationMultiELParamAndRegular.settings.oauth.clientId,
        oidc,
        domain,
        'code',
        multiParamAndRegular.callback1,
      );
      const loginResponse = await login(
        authResponse,
        user.username,
        applicationMultiELParamAndRegular.settings.oauth.clientId,
        user.password,
      );
      const tokenResponse = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse.headers['location']).toContain('https://callback/?param=web&param3=test&param2=web');
      expect(tokenResponse.headers['location']).toContain('code=');
    });

    it('Failing EL evaluation', async () => {
      const authResponse = await initiateLoginFlow(
        applicationFailingELParam.settings.oauth.clientId,
        oidc,
        domain,
        'code',
        failingELParam.callback1,
      );
      const loginResponse = await login(authResponse, user.username, applicationFailingELParam.settings.oauth.clientId, user.password);
      const tokenResponse = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse.headers['location']).toContain('/oauth/error');
      expect(tokenResponse.headers['location']).toContain('error=query_param_parsing_error');
    });

    it('EL uses nonexisting param - should evaluate to empty string', async () => {
      const authResponse = await initiateLoginFlow(
        applicationFailingELParam.settings.oauth.clientId,
        oidc,
        domain,
        'code',
        failingELParam.callback2,
      );
      const loginResponse = await login(authResponse, user.username, applicationFailingELParam.settings.oauth.clientId, user.password);
      const tokenResponse = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse.headers['location']).toContain('https://callback2/?param=&param3=test&param2=web');
      expect(tokenResponse.headers['location']).toContain('code=');
    });
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});
