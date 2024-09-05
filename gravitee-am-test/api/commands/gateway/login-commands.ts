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

export const initiateLoginFlow = async (clientId, openIdConfiguration, domain, responseType = 'code') => {
  const params = `?response_type=${responseType}&client_id=${clientId}&redirect_uri=https://auth-nightly.gravitee.io/myApp/callback`;

  const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);
  const loginLocation = authResponse.headers['location'];

  expect(loginLocation).toBeDefined();
  expect(loginLocation).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/login`);
  expect(loginLocation).toContain(`client_id=${clientId}`);

  return authResponse;
};

export const login = async (authResponse, userName, clientId, password = 'SomeP@ssw0rd', rememberMe = false) => {
  const loginResult = await extractXsrfTokenAndActionResponse(authResponse);
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

export const loginUserNameAndPassword = async (clientId, user, userPassword, rememberMe, openIdConfiguration, domain) => {
  return await loginUser(clientId, user.username, userPassword, rememberMe, openIdConfiguration, domain);
};

export const loginAdditionalInfoAndPassword = async (clientId, additionalInfo, userPassword, rememberMe, openIdConfiguration, domain) => {
  return await loginUser(clientId, additionalInfo, userPassword, rememberMe, openIdConfiguration, domain);
};

export const loginUser = async (clientId, nameOrAdditionalInfo, userPassword, rememberMe, openIdConfiguration, domain) => {
  const authResponse = await initiateLoginFlow(clientId, openIdConfiguration, domain);
  const postLogin = await login(authResponse, nameOrAdditionalInfo, clientId, userPassword, rememberMe);
  //log in failed with error
  if (postLogin.headers['location'].includes('error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+user')) {
    return postLogin;
  }
  const authorize = await getHeaderLocation(postLogin);
  expect(authorize.headers['location']).toBeDefined();

  //log in for the very first time
  if (authorize.headers['location'].includes(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/consent`)) {
    const consent = await performGet(authorize.headers['location'], '', {
      Cookie: authorize.headers['set-cookie'],
    }).expect(200);

    const authorize2 = await postConsent(consent);
    expect(authorize2.headers['location']).toBeDefined();
    expect(authorize2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

    const tokenResponse = await performGet(authorize2.headers['location'], '', {
      Cookie: authorize.headers['set-cookie'],
    }).expect(302);

    expect(tokenResponse.headers['location']).toBeDefined();
    return tokenResponse;
  } else {
    return authorize;
  }
};
