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
import { applicationBase64Token, getBase64BasicAuth } from '@gateway-commands/utils';

const supertest = require('supertest');
const cheerio = require('cheerio');

const URLS = {
  WELL_KNOWN_OPENID_CONFIG: '/oidc/.well-known/openid-configuration',
};

function setHeaders(request, headers: object) {
  if (headers) {
    for (const [k, v] of Object.entries(headers)) {
      request.set(k, v);
    }
  }
}

export const performPost = (baseUrl, uri = '', body = null, headers = null) => {
  const request = supertest(baseUrl).post(uri);
  setHeaders(request, headers);
  return body ? request.send(body) : request.send();
};

export const performFormPost = (baseUrl, uri = '', body, headers) => {
  const request = supertest(baseUrl).post(uri);
  setHeaders(request, headers);
  return request.field(body);
};

export const performGet = (baseUrl, uri = '', headers = null) => {
  const request = supertest(baseUrl).get(uri);
  setHeaders(request, headers);
  return request.send();
};

export const performOptions = (baseUrl, uri = '', headers = null) => {
  const request = supertest(baseUrl).options(uri);
  setHeaders(request, headers);
  return request.send();
};

export const performDelete = (baseUrl, uri = '', headers) => {
  const request = supertest(baseUrl).delete(uri);
  setHeaders(request, headers);
  return request.send();
};

export const getWellKnownOpenIdConfiguration = (domainHrid: string) =>
  performGet(process.env.AM_GATEWAY_URL, `/${domainHrid}${URLS.WELL_KNOWN_OPENID_CONFIG}`);

export const extractXsrfTokenAndActionResponse = async (response) => {
  const headers = response.headers['set-cookie'] ? { Cookie: response.headers['set-cookie'] } : {};
  const result = await performGet(response.headers['location'], '', headers);
  if (result.status == 302) {
    console.error(` Expected 200 from ${result.request.url}, got 302 location=${result.headers['location']}`);
    throw new Error('Expected 200, got 302');
  } else if (result.status != 200) {
    throw new Error(`Expected 200 from ${result.request.url}, got ${result.status}`);
  }
  const dom = cheerio.load(result.text);
  const xsrfToken = dom('[name=X-XSRF-TOKEN]').val();
  const action = dom('form').attr('action');

  expect(xsrfToken).toBeDefined();
  expect(action).toBeDefined();

  return { headers: result.headers, token: xsrfToken, action: action };
};

export const extractXsrfTokenAndHref = async (response, htmlElementId: string) => {
  const headers = response.headers['set-cookie'] ? { Cookie: response.headers['set-cookie'] } : {};
  const result = await performGet(response.headers['location'], '', headers);
  if (result.status == 302) {
    console.error(` Expected 200 from ${result.request.url}, got 302 location=${result.headers['location']}`);
    throw new Error('Expected 200, got 302');
  } else if (result.status != 200) {
    throw new Error(`Expected 200 from ${result.request.url}, got ${result.status}`);
  }
  const dom = cheerio.load(result.text);
  const xsrfToken = dom('[name=X-XSRF-TOKEN]').val();
  const action = dom(`[id=${htmlElementId}]`).attr('href');

  expect(xsrfToken).toBeDefined();
  expect(action).toBeDefined();

  return { headers: result.headers, token: xsrfToken, action: action };
};

export const extractXsrfToken = async (url, parameters) => {
  const result = await performGet(url, parameters).expect(200);
  const dom = cheerio.load(result.text);
  const xsrfToken = dom('[name=X-XSRF-TOKEN]').val();

  expect(xsrfToken).toBeDefined();
  return { headers: result.headers, token: xsrfToken };
};

export const logoutUser = async (uri, postLogin: any, targetUri = null) =>
  performGet(uri, targetUri ? `?post_logout_redirect_uri=${targetUri}` : '', { Cookie: postLogin.headers['set-cookie'] }).expect(302);

export async function signInUser(domain, application: any, user: any, openIdConfiguration, additionalParams = null) {
  const clientId = application.settings.oauth.clientId;
  const redirect_uri = application.settings.oauth.redirectUris[0];

  // TO FIX when it will be useful:
  // - Manage PKCE challenge is the app requires it
  const params =
    `?response_type=code&client_id=${clientId}&redirect_uri=${redirect_uri}` + (additionalParams ? `&${additionalParams}` : '');

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
  const redirectUri = postLoginRedirect.headers['location'];
  const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
  expect(redirectUri).toBeDefined();
  expect(redirectUri).toContain(`${redirect_uri}?`);
  expect(redirectUri).toMatch(codePattern);

  return postLoginRedirect;
}

export async function requestToken(application: any, openIdConfiguration: any, postLogin: any) {
  const redirectUri = postLogin.headers['location'];
  const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
  const authorizationCode = redirectUri.match(codePattern)[1];

  // TO FIX when it will be useful :
  // - Adapt the authentication method based on the application settings
  // - Manage PKCE challenge is the app requires it
  const redirect_uri = application.settings.oauth.redirectUris[0];
  return await performPost(
    openIdConfiguration.token_endpoint,
    `?grant_type=authorization_code&code=${authorizationCode}&redirect_uri=${redirect_uri}`,
    null,
    {
      Authorization: 'Basic ' + applicationBase64Token(application),
    },
  ).expect(200);
}

/**
 * Request access token using client_credentials grant
 * @param clientId - OAuth client ID
 * @param clientSecret - OAuth client secret
 * @param openIdConfiguration - OpenID configuration containing token_endpoint
 * @param scope - Optional scope parameter
 * @param resource - Optional resource identifier parameter (RFC 8707)
 * @returns Promise resolving to access token string
 */
export async function requestClientCredentialsToken(
  clientId: string,
  clientSecret: string,
  openIdConfiguration: any,
  scope?: string,
  resource?: string,
): Promise<string> {
  const tokenParams = new URLSearchParams({
    grant_type: 'client_credentials',
  });
  if (scope) {
    tokenParams.append('scope', scope);
  }
  if (resource) {
    tokenParams.append('resource', resource);
  }

  const response = await performPost(
    openIdConfiguration.token_endpoint,
    '',
    tokenParams.toString(),
    {
      'Content-Type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + getBase64BasicAuth(clientId, clientSecret),
    },
  ).expect(200);

  expect(response.body.access_token).toBeDefined();
  return response.body.access_token;
}
