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

import { extractXsrfTokenAndActionResponse, performFormPost, performGet } from '@gateway-commands/oauth-oidc-commands';
import { expect } from '@jest/globals';

const cheerio = require('cheerio');

export const initiateLoginFlow = async (clientId, openIdConfiguration, domain, responseType = 'code', redirecrUri?) => {
  let redirect = `https://auth-nightly.gravitee.io/myApp/callback`;
  if (redirecrUri != undefined) {
    redirect = redirecrUri;
  }
  const params = `?response_type=${responseType}&client_id=${clientId}&redirect_uri=${redirect}`;

  const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);
  const loginLocation = authResponse.headers['location'];

  expect(loginLocation).toBeDefined();
  expect(loginLocation).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/login`);
  expect(loginLocation).toContain(`client_id=${clientId}`);

  return authResponse;
};

export const login = async (authResponse, userName, clientId, password = 'SomeP@ssw0rd', rememberMe = false, redirectOidc = false) => {
  if (redirectOidc) {
    //This is for testing OIDC as a provider
    const loginResp = await performGet(authResponse.headers['location'], '', { Cookie: authResponse.headers['set-cookie'] }).expect(302);
    authResponse = await performGet(loginResp.headers['location'], '', { Cookie: loginResp.headers['set-cookie'] }).expect(302);
  }
  const loginResult = await extractXsrfTokenAndActionResponse(authResponse);
  if (redirectOidc) {
    const parsedUrl = new URL(loginResult.action);
    clientId = parsedUrl.searchParams.get('client_id');
  }
  return await performFormPost(
    loginResult.action,
    '',
    {
      'X-XSRF-TOKEN': loginResult.token,
      username: userName,
      password: password,
      rememberMe: rememberMe ? 'on' : 'off',
      client_id: clientId,
    },
    {
      Cookie: loginResult.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);
};

export const getHeaderLocation = async (postLogin) => {
  return await performGet(postLogin.headers['location'], '', {
    Cookie: postLogin.headers['set-cookie'],
  }).expect(302);
};

export const postConsent = async (consent) => {
  const dom = cheerio.load(consent.text);
  const xsrfToken = dom('[name=X-XSRF-TOKEN]').val();
  return await performFormPost(
    consent.request.url,
    '',
    {
      'X-XSRF-TOKEN': xsrfToken,
      'scope.openid': true,
      user_oauth_approval: true,
    },
    {
      Cookie: consent.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);
};

export const loginUserNameAndPassword = async (
  clientId: any,
  user: any,
  userPassword: any,
  rememberMe: any,
  openIdConfiguration: any,
  domain: any,
  redirectUri?: any,
  responseType?: any,
  redirectOidc?: any,
) => {
  return await loginUser(
    clientId,
    user.username,
    userPassword,
    rememberMe,
    openIdConfiguration,
    domain,
    redirectUri,
    responseType,
    redirectOidc,
  );
};

export const loginAdditionalInfoAndPassword = async (clientId, additionalInfo, userPassword, rememberMe, openIdConfiguration, domain) => {
  return await loginUser(clientId, additionalInfo, userPassword, rememberMe, openIdConfiguration, domain);
};

export const loginUser = async (
  clientId: any,
  nameOrAdditionalInfo: any,
  userPassword: any,
  rememberMe: any,
  openIdConfiguration: any,
  domain: any,
  redirectUri?: any,
  responseType?: any,
  redirectOidc?: any,
) => {
  let authResponse;
  if (responseType != undefined) {
    authResponse = await initiateLoginFlow(clientId, openIdConfiguration, domain, responseType, redirectUri);
  } else {
    authResponse = await initiateLoginFlow(clientId, openIdConfiguration, domain, redirectUri);
  }
  const postLogin = await login(authResponse, nameOrAdditionalInfo, clientId, userPassword, rememberMe, redirectOidc);
  //log in failed with an error
  if (postLogin.headers['location'].includes('error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+user')) {
    return postLogin;
  }
  const authorize = await getHeaderLocation(postLogin);
  expect(authorize.headers['location']).toBeDefined();

  //log in for the very first time
  if (authorize.headers['location'].includes('/oauth/consent')) {
    const consent = await performGet(authorize.headers['location'], '', {
      Cookie: authorize.headers['set-cookie'],
    }).expect(200);

    const authorize2 = await postConsent(consent);
    expect(authorize2.headers['location']).toBeDefined();
    expect(authorize2.headers['location']).toContain(`/oauth/authorize`);

    const tokenResponse = await performGet(authorize2.headers['location'], '', {
      Cookie: authorize2.headers['set-cookie'],
    }).expect(302);
    expect(tokenResponse.headers['location']).toBeDefined();
    if (redirectOidc) {
      const authorize3 = await performGet(tokenResponse.headers['location'], '', { Cookie: tokenResponse.headers['set-cookie'] }).expect(
        302,
      );
      const tokenResponse2 = await performGet(authorize3.headers['location'], '', {
        Cookie: authorize3.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse2.headers['location']).toBeDefined();
      return tokenResponse2;
    }
    return tokenResponse;
  } else {
    if (redirectOidc) {
      const authorize4 = await performGet(authorize.headers['location'], '', { Cookie: authorize.headers['set-cookie'] }).expect(302);
      const tokenResponse4 = await performGet(authorize4.headers['location'], '', {
        Cookie: authorize4.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse4.headers['location']).toBeDefined();
      return tokenResponse4;
    }
    return authorize;
  }
};
