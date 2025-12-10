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
import { safeDeleteDomain, patchDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { delay, uniqueName } from '@utils-commands/misc';
import { buildTestUser, createUser } from '@management-commands/user-management-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { privateJwk, publicJwk } from '@api-fixtures/oidc';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import jwt from 'jsonwebtoken';
import jwkToPem from 'jwk-to-pem';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';

global.fetch = fetch;

let accessToken: any;
let cibaDomain: any;
let nonCibaDomain: any;
let cibaUser: any;
let cibaApp: any;
const managementUrl = `${process.env.AM_MANAGEMENT_URL}/management/organizations/DEFAULT/environments/DEFAULT/domains/`;
const cibaUrl = `${process.env.AM_CIBA_NOTIFIER_URL}`;
const internalCibaUrl = `${process.env.AM_INTERNAL_CIBA_NOTIFIER_URL}`;


jest.setTimeout(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  cibaDomain = await setupDomainForTest(uniqueName('ciba', true), { accessToken }).then((it) => it.domain);
  nonCibaDomain = await setupDomainForTest(uniqueName('ciba-disabled', true), { accessToken }).then((it) => it.domain);
  cibaDomain = await patchDomain(cibaDomain.id, accessToken, {
    oidc: {
      clientRegistrationSettings: {
        allowLocalhostRedirectUri: true,
        allowHttpSchemeRedirectUri: true,
        allowWildCardRedirectUri: true,
        isDynamicClientRegistrationEnabled: true,
        isOpenDynamicClientRegistrationEnabled: true,
      },
      cibaSettings: {
        authReqExpiry: 600,
        tokenReqInterval: 5,
        bindingMessageLength: 256,
        deviceNotifiers: [],
        enabled: true,
      },
    },
  });

  nonCibaDomain = await patchDomain(nonCibaDomain.id, accessToken, {
    oidc: {
      clientRegistrationSettings: {
        allowLocalhostRedirectUri: true,
        allowHttpSchemeRedirectUri: true,
        allowWildCardRedirectUri: true,
        isDynamicClientRegistrationEnabled: true,
        isOpenDynamicClientRegistrationEnabled: true,
      },
      cibaSettings: {
        enabled: false,
      },
    },
  });
  cibaUser = await createUser(cibaDomain.id, accessToken, buildTestUser(0));
  await delay(6000);
});

describe('Create CIBA application', () => {
  it('INVALID - CIBA App forbiden not CIBA domain', async () => {
    const response = await performPost(
      `${process.env.AM_GATEWAY_URL}/${nonCibaDomain.hrid}/oidc/register`,
      '',
      {
        redirect_uris: ['https://callback'],
        client_name: 'CIBA App',
        application_type: 'web',
        grant_types: ['authorization_code', 'refresh_token', 'urn:openid:params:grant-type:ciba'],
        response_types: ['code'],
        backchannel_token_delivery_mode: 'poll',
      },
      {
        'Content-type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
    );
    expect(response.status).toBe(400);
    expect(response.body.error).toBe('invalid_client_metadata');
    expect(response.body.error_description).toBe('CIBA flow not supported');
  });

  it('INVALID - No Backend Delivery Mode', async () => {
    const response = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc/register`,
      '',
      {
        redirect_uris: ['https://callback'],
        client_name: 'CIBA App',
        application_type: 'web',
        grant_types: ['authorization_code', 'refresh_token', 'urn:openid:params:grant-type:ciba'],
        response_types: ['code', 'code id_token token', 'code id_token', 'code token'],
        scope: 'openid',
        default_acr_values: ['urn:mace:incommon:iap:silver'],
        token_endpoint_auth_method: 'client_secret_basic',
        backchannel_authentication_request_signing_alg: 'RS256',
        client_id: 'ciba',
        client_secret: 'ciba',
      },
      {
        'Content-type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
    );

    expect(response.status).toBe(400);
    expect(response.body.error).toBe('invalid_client_metadata');
    expect(response.body.error_description).toBe('The backchannel_token_delivery_mode is required for CIBA Flow.');
  });

  it('INVALID - Unsupported Backend Delivery Mode', async () => {
    const response = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc/register`,
      '',
      {
        redirect_uris: ['https://callback'],
        client_name: 'CIBA App',
        application_type: 'web',
        grant_types: ['authorization_code', 'refresh_token', 'urn:openid:params:grant-type:ciba'],
        response_types: ['code', 'code id_token token', 'code id_token', 'code token'],
        scope: 'openid',
        default_acr_values: ['urn:mace:incommon:iap:silver'],
        token_endpoint_auth_method: 'client_secret_basic',
        backchannel_token_delivery_mode: 'unsupported',
        backchannel_authentication_request_signing_alg: 'RS256',
        client_id: 'ciba',
        client_secret: 'ciba',
      },
      {
        'Content-type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
    );

    expect(response.status).toBe(400);
    expect(response.body.error).toBe('invalid_client_metadata');
    expect(response.body.error_description).toBe('Unsupported backchannel_token_delivery_mode');
  });

  it('VALID - Create CIBA App', async () => {
    const response = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc/register`,
      '',
      {
        redirect_uris: ['https://callback'],
        client_name: 'CIBA App',
        application_type: 'web',
        grant_types: ['authorization_code', 'refresh_token', 'urn:openid:params:grant-type:ciba'],
        response_types: ['code', 'code id_token token', 'code id_token', 'code token'],
        scope: 'openid profile',
        default_acr_values: ['urn:mace:incommon:iap:silver'],
        token_endpoint_auth_method: 'client_secret_basic',
        backchannel_token_delivery_mode: 'poll',
        backchannel_authentication_request_signing_alg: 'RS256',
        client_id: 'ciba',
        client_secret: 'ciba',
        jwks: { keys: publicJwk },
        request_object_signing_alg: 'RS256',
      },
      {
        'Content-type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
    );
    expect(response.status).toBe(201);
    cibaApp = response.body;
    expect(response.body.client_name).toBe('CIBA App');
    expect(response.body.application_type).toBe('web');
    expect(response.body.grant_types).toContain('authorization_code');
    expect(response.body.response_types).toContain('code');
  });

  it('Assign default IDP', async () => {
    const idps = await getAllIdps(cibaDomain.id, accessToken);
    await patchApplication(
      cibaDomain.id,
      accessToken,
      {
        identityProviders: [
          {
            identity: idps[0].id,
            priority: -1,
          },
        ],
      },
      cibaApp.id,
    );
  });
});
describe('CIBA invalid Flow', () => {
  it('Initialize CIBA Authentication Flow - Missing User Hints', async () => {
    const response = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc/ciba/authenticate`,
      '',
      'scope=openid profile',
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(cibaApp.client_id, cibaApp.client_secret),
      },
    );
    expect(response.status).toBe(400);
    expect(response.body.error).toContain('invalid_request');
  });

  it('Initialize CIBA Authentication Flow - Invalid Scope', async () => {
    const response = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc/ciba/authenticate`,
      '',
      `scope=openid unknown&login_hint=${cibaUser.username}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(cibaApp.client_id, cibaApp.client_secret),
      },
    );
    expect(response.status).toBe(400);
    expect(response.body.error).toContain('invalid_scope');
  });

  it('Initialize CIBA Authentication Flow - Multiple Release User Hints', async () => {
    const response = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc/ciba/authenticate`,
      '',
      `scope=openid unknown&login_hint=${cibaUser.username}&id_token_hint=something`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(cibaApp.client_id, cibaApp.client_secret),
      },
    );
    expect(response.status).toBe(400);
    expect(response.body.error).toContain('invalid_request');
  });

  it('Initialize CIBA Authentication Flow - Invalid ACR', async () => {
    const response = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc/ciba/authenticate`,
      '',
      `scope=openid unknown&login_hint=${cibaUser.username}&acr_values=invalid`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(cibaApp.client_id, cibaApp.client_secret),
      },
    );
    expect(response.status).toBe(400);
    expect(response.body.error).toContain('invalid_request');
    expect(response.body.error_description).toContain('Unsupported acr values');
  });

  it('Initialize CIBA Authentication Flow - Binding Message Too Long', async () => {
    const response = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc/ciba/authenticate`,
      '',
      `scope=openid unknown&login_hint=${cibaUser.username}&binding_message=abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(cibaApp.client_id, cibaApp.client_secret),
      },
    );
    expect(response.status).toBe(400);
    expect(response.body.error).toContain('invalid_binding_message');
  });
});

describe('CIBA valid Flow', () => {
  it('Prepare', async () => {
    const response = await performPost(
      `${managementUrl}${cibaDomain.id}/auth-device-notifiers`,
      '',
      {
        type: 'http-am-authdevice-notifier',
        configuration:
          `{"endpoint":"${internalCibaUrl}/notify/accept-all","headerName":"Authorization","connectTimeout":5000,"idleTimeout":10000,"maxPoolSize":10}`,
        name: 'Always OK notifier',
      },
      {
        'Content-type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
    );
    expect(response.status).toBe(201);
    const cibaNotifierPluginOK = response.body.id;

    await patchDomain(cibaDomain.id, accessToken, {
      oidc: {
        clientRegistrationSettings: {
          allowLocalhostRedirectUri: true,
          allowHttpSchemeRedirectUri: true,
          allowWildCardRedirectUri: true,
          isDynamicClientRegistrationEnabled: true,
          isOpenDynamicClientRegistrationEnabled: true,
        },
        cibaSettings: {
          authReqExpiry: 600,
          tokenReqInterval: 5,
          bindingMessageLength: 256,
          deviceNotifiers: [{ id: cibaNotifierPluginOK }],
          enabled: true,
        },
      },
    });
    await delay(6000);
    const cibaResponse = await performPost(
      `${cibaUrl}/domains`,
      '',
      {
        domainId: cibaDomain.id,
        domainCallback: `${process.env.AM_INTERNAL_GATEWAY_URL}/${cibaDomain.hrid}/oidc/ciba/authenticate/callback`,
        clientId: cibaApp.client_id,
        clientSecret: cibaApp.client_secret,
      },
      {
        'Content-type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
    );
    expect(cibaResponse.status).toBe(200);
  });
  it('With login as Hint', async () => {
    const response1 = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc/ciba/authenticate`,
      '',
      `scope=openid profile&login_hint=${cibaUser.username}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(cibaApp.client_id, cibaApp.client_secret),
      },
    );
    expect(response1.status).toBe(200);
    expect(response1.body.auth_req_id).toBeDefined();
    expect(response1.body.expires_in).toBeDefined();
    expect(response1.body.interval).toEqual(5);
    const auth_req_id = response1.body.auth_req_id;

    // wait 6 sec before new request
    await delay(6000);
    //Request CIBA access token
    const response2 = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oauth/token?auth_req_id=${auth_req_id}&grant_type=urn:openid:params:grant-type:ciba`,
      '',
      {},
      {
        Authorization: 'Basic ' + getBase64BasicAuth(cibaApp.client_id, cibaApp.client_secret),
      },
    );
    expect(response2.status).toBe(200);
    expect(response2.body.access_token).toBeDefined();
    expect(response2.body.id_token).toBeDefined();
    const ciba_access_token = response2.body.access_token;
    //Request user info
    const response3 = await performGet(`${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc/userinfo`, '', {
      Authorization: `Bearer ${ciba_access_token}`,
    });
    expect(response3.status).toBe(200);
  });

  it('With id_token_hint as Hint', async () => {
    const openIdConfiguration = {
      authorization_endpoint: `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oauth/authorize`,
    };
    const postAuthResponse = await loginUserNameAndPassword(
      cibaApp.client_id,
      cibaUser,
      'SomeP@assw0rd',
      false,
      openIdConfiguration,
      cibaDomain,
      'https://callback&scope=openid&nonce=321654987',
      'code id_token',
    );

    const postAuthLocation = postAuthResponse.header.location;
    const fragment = postAuthLocation.substr(postAuthLocation.indexOf('#') + 1);
    const cibaIdTokenHint = fragment
      .split('&')
      .filter((kv) => kv.startsWith('id_token'))
      .map((kv) => kv.split('=')[1])[0];

    //Initialize CIBA Authentication Flow - id_token_hint
    const cibaAuthResponse = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc/ciba/authenticate`,
      '',
      `scope=openid profile&id_token_hint=${cibaIdTokenHint}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(cibaApp.client_id, cibaApp.client_secret),
      },
    );

    expect(cibaAuthResponse.status).toBe(200);
    expect(cibaAuthResponse.body.auth_req_id).toBeDefined();
    expect(cibaAuthResponse.body.expires_in).toBeDefined();
    expect(cibaAuthResponse.body.interval).toEqual(5);
    const auth_req_id = cibaAuthResponse.body.auth_req_id;

    // wait 10 sec before new request
    await delay(10000);
    //Request CIBA AccessToken
    const tokenResponse = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oauth/token?auth_req_id=${auth_req_id}&grant_type=urn:openid:params:grant-type:ciba`,
      '',
      {},
      {
        Authorization: 'Basic ' + getBase64BasicAuth(cibaApp.client_id, cibaApp.client_secret),
      },
    );
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.body.access_token).toBeDefined();
    expect(tokenResponse.body.id_token).toBeDefined();
    const ciba_access_token = tokenResponse.body.access_token;
    //Get UserProfile using CIBA Access Token
    const userInfoResponse = await performGet(`${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc/userinfo`, '', {
      Authorization: `Bearer ${ciba_access_token}`,
    });
    expect(userInfoResponse.status).toBe(200);
    //Logout user
    const logoutResponse = await performGet(`${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/logout`, '');
    expect(logoutResponse.status).toBe(302);
  });

  it('With login as Hint - RequestObject', async () => {
    const currentTime = Date.now() / 1000;
    const payload = {
      aud: `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc`,
      iss: cibaApp.client_id,
      jti: 'abcd',
      nbf: currentTime,
      scope: 'openid profile',
      login_hint: cibaUser.username,
    };

    const options = { expiresIn: '5m', keyid: '123', algorithm: 'RS256' };
    const privatePEM = jwkToPem(privateJwk, { private: true });
    const requestObject = jwt.sign(payload, privatePEM, options);

    const response1 = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc/ciba/authenticate`,
      '',
      `request=${requestObject}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(cibaApp.client_id, cibaApp.client_secret),
      },
    );
    expect(response1.status).toBe(200);
    expect(response1.body.auth_req_id).toBeDefined();
    expect(response1.body.expires_in).toBeDefined();
    expect(response1.body.interval).toEqual(5);
    const auth_req_id = response1.body.auth_req_id;
    // wait 10 sec before new request
    await delay(10000);
    //Request CIBA access token
    const response2 = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oauth/token?auth_req_id=${auth_req_id}&grant_type=urn:openid:params:grant-type:ciba`,
      '',
      {},
      {
        Authorization: 'Basic ' + getBase64BasicAuth(cibaApp.client_id, cibaApp.client_secret),
      },
    );
    expect(response2.status).toBe(200);
    expect(response2.body.access_token).toBeDefined();
    expect(response2.body.id_token).toBeDefined();
    const ciba_access_token = response2.body.access_token;
    //Get UserProfile using CIBA Access Token
    const response3 = await performGet(`${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc/userinfo`, '', {
      Authorization: `Bearer ${ciba_access_token}`,
    });
    expect(response3.status).toBe(200);
  });

  it('Request Rejected by User', async () => {
    const response = await performPost(
      `${managementUrl}${cibaDomain.id}/auth-device-notifiers`,
      '',
      {
        type: 'http-am-authdevice-notifier',
        configuration:
          `{"endpoint":"${internalCibaUrl}/notify/reject-all","headerName":"Authorization","connectTimeout":5000,"idleTimeout":10000,"maxPoolSize":10}`,
        name: 'Always reject notifier',
      },
      {
        'Content-type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
    );
    expect(response.status).toBe(201);
    const cibaNotifierPluginReject = response.body.id;

    await patchDomain(cibaDomain.id, accessToken, {
      oidc: {
        clientRegistrationSettings: {
          allowLocalhostRedirectUri: true,
          allowHttpSchemeRedirectUri: true,
          allowWildCardRedirectUri: true,
          isDynamicClientRegistrationEnabled: true,
          isOpenDynamicClientRegistrationEnabled: true,
        },
        cibaSettings: {
          authReqExpiry: 600,
          tokenReqInterval: 5,
          bindingMessageLength: 256,
          deviceNotifiers: [{ id: cibaNotifierPluginReject }],
          enabled: true,
        },
      },
    });

    await delay(6000);

    const response1 = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oidc/ciba/authenticate`,
      '',
      `scope=openid profile&login_hint=${cibaUser.username}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(cibaApp.client_id, cibaApp.client_secret),
      },
    );
    expect(response1.status).toBe(200);
    expect(response1.body.auth_req_id).toBeDefined();
    expect(response1.body.expires_in).toBeDefined();
    expect(response1.body.interval).toEqual(5);
    const auth_req_id = response1.body.auth_req_id;

    // wait 6 sec before new request
    await delay(6000);
    const response2 = await performPost(
      `${process.env.AM_GATEWAY_URL}/${cibaDomain.hrid}/oauth/token?auth_req_id=${auth_req_id}&grant_type=urn:openid:params:grant-type:ciba`,
      '',
      {},
      {
        Authorization: 'Basic ' + getBase64BasicAuth(cibaApp.client_id, cibaApp.client_secret),
      },
    );
    expect(response2.status).toBe(400);
    expect(response2.body.error).toBe('access_denied');
  });
});

afterAll(async () => {
  if (cibaDomain && cibaDomain.id) {
    await safeDeleteDomain(cibaDomain.id, accessToken);
  }
  if (nonCibaDomain && nonCibaDomain.id) {
    await safeDeleteDomain(nonCibaDomain.id, accessToken);
  }
});
