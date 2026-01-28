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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  DomainOidcConfig,
  patchDomain,
  startDomain,
  waitFor,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { extractXsrfTokenAndHref, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { initiateLoginFlow } from '@gateway-commands/login-commands';
import { uniqueName } from '@utils-commands/misc';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { enrollCertificate } from '@management-commands/certificate-credential-management-commands';
import { Domain } from '@management-models/Domain';
import { Agent } from 'https';
import fs from 'fs';
import path from 'path';
import process from 'node:process';
import { setup } from '../test-fixture';

setup(20000);

interface UserPem {
  user: any;
  pem: string;
  key: string;
}

let accessToken;
let domain: Domain;
let oidc: DomainOidcConfig;
let app;
let userWithCred: UserPem;
let userWithoutCred: UserPem;
let userDifferentCa: UserPem;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();

  await createDomain(accessToken, uniqueName('cba-domain', true), 'CBA')
    .then((d) =>
      patchDomain(d.id, accessToken, {
        loginSettings: {
          certificateBasedAuthEnabled: true,
          certificateBasedAuthUrl: `${process.env.MTLS_URL}`,
        },
      }),
    )
    .then((d) =>
      startDomain(d.id, accessToken)
        .then(waitForDomainStart)
        .then((result) => {
          domain = result.domain;
          oidc = result.oidcConfig;
        }),
    );

  const idpSet = await getAllIdps(domain.id, accessToken);
  const defaultIdp = idpSet.values().next().value;

  app = await createApplication(domain.id, accessToken, {
    name: 'test',
    type: 'WEB',
    clientId: 'test',
    clientSecret: 'test',
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
        identityProviders: [{ identity: defaultIdp.id, priority: -1 }],
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  const pem1 = readFile('/certs/client-a/client.crt');
  const key1 = readFile('/certs/client-a/client.key');
  userWithCred = await createUser(domain.id, accessToken, {
    firstName: 'john',
    lastName: 'doe',
    email: `with@test.com`,
    username: 'with',
    password: 'Password123!',
    client: app.id,
    source: defaultIdp.id,
  }).then((user) =>
    enrollCertificate(domain.id, user.id, accessToken, { certificatePem: pem1 }).then((cert) => {
      return {
        user: user,
        pem: cert.certificatePem,
        key: key1,
      };
    }),
  );

  const pem2 = readFile('/certs/client-b/client.crt');
  const key2 = readFile('/certs/client-b/client.key');
  userWithoutCred = await createUser(domain.id, accessToken, {
    firstName: 'with',
    lastName: 'out',
    email: `without@test.com`,
    username: 'without',
    password: 'Password123!',
    client: app.id,
    source: defaultIdp.id,
  }).then((user) => {
    return {
      user: user,
      pem: pem2,
      key: key2,
    };
  });

  const pem3 = readFile('/certs/client-c-diff-ca/client.crt');
  const key3 = readFile('/certs/client-c-diff-ca/client.key');
  userDifferentCa = await createUser(domain.id, accessToken, {
    firstName: 'with',
    lastName: 'out',
    email: `invalid@test.com`,
    username: 'invalid',
    password: 'Password123!',
    client: app.id,
    source: defaultIdp.id,
  }).then((user) =>
    enrollCertificate(domain.id, user.id, accessToken, { certificatePem: pem3 }).then((cert) => {
      return {
        user: user,
        pem: cert.certificatePem,
        key: key3,
      };
    }),
  );

  await waitFor(5000);
});

describe('Should authenticate user', () => {
  it('with enrolled certificate', async () => {
    const clientId = app.settings.oauth.clientId;

    const authResponse = await initiateLoginFlow(clientId, oidc, domain, 'code', 'https://callback');
    const loginPage = await extractXsrfTokenAndHref(authResponse, 'cbaLinkButton');
    const cbaResponse = await performPost(loginPage.action, '', { Cookie: loginPage.headers['set-cookie'] });

    const agent = new Agent({
      rejectUnauthorized: false,
      cert: userWithCred.pem,
      key: userWithCred.key,
    });

    const authPage = await fetch(cbaResponse.headers['location'], {
      method: 'GET',
      redirect: 'manual',
      agent,
    } as any);

    const callbackResponse = await performGet(authPage.headers.get('location'), '', { Cookie: loginPage.headers['set-cookie'] });
    const authorizeResponse = await performGet(callbackResponse.headers['location'], '', {
      Cookie: callbackResponse.headers['set-cookie'],
    });
    expect(authorizeResponse.headers['location']).toContain('code=');
    expect(authorizeResponse.headers['location']).toContain('https://callback');
  });
});

describe('Should not authenticate user', () => {
  it('with enrolled certificate with unknown CA', async () => {
    const clientId = app.settings.oauth.clientId;

    const authResponse = await initiateLoginFlow(clientId, oidc, domain, 'code', 'https://callback');
    const loginPage = await extractXsrfTokenAndHref(authResponse, 'cbaLinkButton');
    const cbaResponse = await performPost(loginPage.action, '', { Cookie: loginPage.headers['set-cookie'] });

    const agent = new Agent({
      rejectUnauthorized: false,
      cert: userDifferentCa.pem,
      key: userDifferentCa.key,
    });

    const authPage = await fetch(cbaResponse.headers['location'], {
      method: 'GET',
      redirect: 'manual',
      agent,
    } as any);

    expect(authPage.status).toBe(400);
  });

  it('without enrolled certificate', async () => {
    const clientId = app.settings.oauth.clientId;

    const authResponse = await initiateLoginFlow(clientId, oidc, domain, 'code', 'https://callback');
    const loginPage = await extractXsrfTokenAndHref(authResponse, 'cbaLinkButton');
    const cbaResponse = await performPost(loginPage.action, '', { Cookie: loginPage.headers['set-cookie'] });

    const agent = new Agent({
      rejectUnauthorized: false,
      cert: userWithoutCred.pem,
      key: userWithoutCred.key,
    });

    const authPage = await fetch(cbaResponse.headers['location'], {
      method: 'GET',
      redirect: 'manual',
      agent,
    } as any);

    expect(authPage.headers.get('location')).toContain('error=Unauthorized');
    expect(authPage.headers.get('location')).toContain('Bad+credentials');
    expect(authPage.status).toBe(302);
  });
});

export function readFile(filePath: string) {
  return fs.readFileSync(path.join(process.cwd(), filePath), 'utf8');
}
