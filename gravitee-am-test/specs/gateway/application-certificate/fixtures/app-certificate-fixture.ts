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
import { readFileSync } from 'fs';
import { join } from 'path';
import * as jose from 'jose';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { DomainOidcConfig, safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createApplication, patchApplication, updateApplication } from '@management-commands/application-management-commands';
import { createCertificate, getPublicKeys } from '@management-commands/certificate-management-commands';
import { createIdp, deleteIdp } from '@management-commands/idp-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { buildCertificate } from '@api-fixtures/certificates';
import { createPKCS12CertificateRequest } from '../../../management/certificates/fixtures/certificates-fixture';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

export const APP_CERTIFICATE = {
  DOMAIN_PREFIX: 'app-certificate',
  // Keystore aliases become the `kid` of the keys they sign with
  JKS_ALIAS: 'mytestkey',
  PKCS12_ALIAS: 'test',
  USER_PASSWORD: '#CoMpL3X-P@SsW0Rd',
} as const;

export interface SigningCertificate {
  id: string;
  alias: string;
  // Public key from the certificate as published by the management API, ready for signature verification
  publicKey: jose.KeyLike;
  jwk: jose.JWK;
}

export interface AppCertificateFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  jks: SigningCertificate;
  pkcs12: SigningCertificate;
  jksApp: Application;
  pkcs12App: Application;
  // No certificate assigned: signs with the domain's default certificate
  defaultApp: Application;
  // Starts on the JKS certificate; the reassignment test moves it to PKCS12
  reassignApp: Application;
  user: { username: string; password: string };
}

export const setupAppCertificateFixture = async (): Promise<AppCertificateFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;
  try {
    const started = await setupDomainForTest(uniqueName(APP_CERTIFICATE.DOMAIN_PREFIX, true), { accessToken, waitForStart: true });
    domain = started.domain;

    await deleteIdp(domain.id, accessToken, 'default-idp-' + domain.id);
    const user = { username: uniqueName('cert-user', true), password: APP_CERTIFICATE.USER_PASSWORD };
    const idp = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'inline-am-idp',
      domainWhitelist: [],
      configuration: JSON.stringify({ users: [{ firstname: 'Cert', lastname: 'User', ...user }] }),
      name: 'app-certificate-idp',
    });

    const jks = await createSigningCertificate(domain, accessToken, buildCertificate(0), APP_CERTIFICATE.JKS_ALIAS);
    const pkcs12 = await createSigningCertificate(domain, accessToken, pkcs12Request(), APP_CERTIFICATE.PKCS12_ALIAS);
    // Two distinct key pairs, otherwise a token could not tell the tests which certificate signed it
    expect(pkcs12.jwk.n).not.toEqual(jks.jwk.n);

    const jksApp = await createTestApp(domain, accessToken, idp.id, 'jks-app', jks.id);
    const pkcs12App = await createTestApp(domain, accessToken, idp.id, 'pkcs12-app', pkcs12.id);
    const defaultApp = await createTestApp(domain, accessToken, idp.id, 'default-app');
    // The last mutation is wrapped so the gateway has picked up everything created above
    const reassignApp = await waitForSyncAfter(domain.id, () => createTestApp(domain, accessToken, idp.id, 'reassign-app', jks.id));

    return {
      accessToken,
      domain,
      oidc: started.oidcConfig,
      jks,
      pkcs12,
      jksApp,
      pkcs12App,
      defaultApp,
      reassignApp,
      user,
      cleanUp: async () => {
        if (domain?.id && accessToken) {
          await safeDeleteDomain(domain.id, accessToken);
        }
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      await safeDeleteDomain(domain.id, accessToken).catch((e) => console.error('Cleanup failed:', e));
    }
    throw error;
  }
};

const pkcs12Request = () => {
  const p12 = join(__dirname, '../../../management/certificates/fixtures/test.p12');
  return createPKCS12CertificateRequest({
    password: 'changeit',
    alias: APP_CERTIFICATE.PKCS12_ALIAS,
    content: readFileSync(p12).toString('base64'),
    type: 'pkcs12-am-certificate',
    contentType: 'application/x-pkcs12',
  });
};

async function createSigningCertificate(domain: Domain, accessToken: string, request, alias: string): Promise<SigningCertificate> {
  const certificate = await createCertificate(domain.id, accessToken, request);
  const keys = await getPublicKeys(domain.id, accessToken, certificate.id);
  const pem = keys.find((key) => key.fmt === 'PEM')?.payload;
  expect(pem).toEqual(expect.stringContaining('-----BEGIN CERTIFICATE-----'));
  const publicKey = await jose.importX509(pem, 'RS256');
  return { id: certificate.id, alias, publicKey, jwk: await jose.exportJWK(publicKey) };
}

async function createTestApp(
  domain: Domain,
  accessToken: string,
  idpId: string,
  name: string,
  certificateId?: string,
): Promise<Application> {
  const created = await createApplication(domain.id, accessToken, {
    name: uniqueName(name, true),
    type: 'WEB',
    redirectUris: ['https://example.com/callback'],
  });
  const updated = await updateApplication(
    domain.id,
    accessToken,
    {
      settings: {
        oauth: {
          redirectUris: ['https://example.com/callback'],
          grantTypes: ['client_credentials', 'password'],
          scopeSettings: [{ scope: 'openid', defaultScope: false }],
        },
      },
      identityProviders: new Set([{ identity: idpId, priority: 0 }]),
    },
    created.id,
  );
  const app = certificateId ? await patchApplication(domain.id, accessToken, { certificate: certificateId }, created.id) : updated;
  // Responses to updates omit the secret; keep the one issued on creation for Basic auth
  app.settings.oauth.clientSecret = created.settings.oauth.clientSecret;
  return app;
}

/** Points the application at another domain certificate and waits for the gateway to pick it up. */
export const assignCertificate = (fixture: AppCertificateFixture, app: Application, certificateId: string) =>
  waitForSyncAfter(fixture.domain.id, () =>
    patchApplication(fixture.domain.id, fixture.accessToken, { certificate: certificateId }, app.id),
  );

export const requestClientCredentialsToken = async (fixture: AppCertificateFixture, app: Application): Promise<string> => {
  const response = await performPost(fixture.oidc.token_endpoint, '', 'grant_type=client_credentials', {
    'Content-type': 'application/x-www-form-urlencoded',
    Authorization: 'Basic ' + applicationBase64Token(app),
  }).expect(200);
  return response.body.access_token;
};

export const requestPasswordTokens = async (fixture: AppCertificateFixture, app: Application) => {
  const { username, password } = fixture.user;
  const response = await performPost(
    fixture.oidc.token_endpoint,
    '',
    `grant_type=password&username=${username}&password=${encodeURIComponent(password)}&scope=openid`,
    { 'Content-type': 'application/x-www-form-urlencoded', Authorization: 'Basic ' + applicationBase64Token(app) },
  ).expect(200);
  return { accessToken: response.body.access_token as string, idToken: response.body.id_token as string };
};

export const kidOf = (token: string): string => jose.decodeProtectedHeader(token).kid;

/** Resolves when the token's signature verifies with the given public key, rejects otherwise. */
export const verifySignature = (token: string, publicKey: jose.KeyLike) => jose.jwtVerify(token, publicKey);

export const fetchJwks = async (fixture: AppCertificateFixture): Promise<jose.JWK[]> => {
  const response = await performGet(fixture.oidc.jwks_uri).expect(200);
  return response.body.keys;
};
