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
import { performFormPost, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { ResponseError } from '../../../../api/management/runtime';
import { TestSuiteContext } from './mfa-setup-fixture';
import { extractDomAttr, extractDomValue } from './mfa-extract-fixture';
import { expect } from '@jest/globals';

export async function get(uri: string, expectedStatus: number, headers: any = null, expectedLocation: string = null) {
  const response = await performGet(uri, '', headers).expect(expectedStatus);
  if (response?.headers['location']?.includes('error')) {
    throw new ResponseError(response, 'error in Location');
  }
  if (expectedLocation && !response?.headers['location']?.includes(expectedLocation)) {
    throw new ResponseError(response, `expectedLocation=${expectedLocation}, Location=${response?.headers['location']}`);
  }
  return response;
}

export async function followUpGet(response, expectedStatus: number, expectedLocation: string = null) {
  const headers = {
    ...response.headers,
    Cookie: response.headers['set-cookie'],
  };
  return get(response.headers['location'], expectedStatus, headers, expectedLocation);
}

export async function postForm(uri: string, body: any, headers: any, expectedStatus: number, expectedLocation: string = null) {
  const response = await performFormPost(uri, '', body, headers).expect(expectedStatus);
  if (response?.headers['location']?.includes('error')) {
    throw new ResponseError(response, 'error in Location');
  }
  if (expectedLocation && !response?.headers['location']?.includes(expectedLocation)) {
    throw new ResponseError(response, `expectedLocation=${expectedLocation}, Location=${response?.headers['location']}`);
  }
  return response;
}

export async function processMfaEnrollment(ctx: TestSuiteContext, rememberDevice: boolean = false, devId?) {
  const enrollLocationResponse = await processLoginFromContext(ctx, rememberDevice, devId);

  const enrollmentPage = await followUpGet(enrollLocationResponse, 200);

  const xsrf = extractDomValue(enrollmentPage, '[name=X-XSRF-TOKEN]');
  const action = extractDomAttr(enrollmentPage, 'form', 'action');
  const factorId = extractDomValue(enrollmentPage, '[name=factorId]');

  const enrollmentPostResponse = await postForm(
    action,
    {
      'X-XSRF-TOKEN': xsrf,
      factorId: factorId,
      ser_mfa_enrollment: true,
    },
    {
      Cookie: enrollmentPage.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
    302,
  );

  return {
    cookie: enrollmentPostResponse.headers['set-cookie'],
    location: enrollmentPostResponse.headers['location'],
    factorId: factorId,
  };
}

export async function processMfaEndToEnd(ctx: TestSuiteContext, rememberDevice: boolean = false, deviceId?) {
  const enrollmentPostResponse = await processMfaEnrollment(ctx, rememberDevice, deviceId);

  const challengeLocationResponse = await get(enrollmentPostResponse.location, 302, { Cookie: enrollmentPostResponse.cookie });
  const challengePage = await followUpGet(challengeLocationResponse, 200);

  let xsrf = extractDomValue(challengePage, '[name=X-XSRF-TOKEN]');
  let action = extractDomAttr(challengePage, 'form', 'action');

  const challengePostResponse = await postForm(
    action,
    {
      'X-XSRF-TOKEN': xsrf,
      factorId: enrollmentPostResponse.factorId,
      code: '1234',
      rememberDeviceConsent: rememberDevice ? 'on' : 'off',
    },
    {
      Cookie: challengePage.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
    302,
  );

  const finalLocationResponse = await followUpGet(challengePostResponse, 302);
  expect(finalLocationResponse.headers['location']).toBeDefined();
  expect(finalLocationResponse.headers['location']).toContain('code=');

  return {
    cookie: finalLocationResponse.headers['set-cookie'],
    rememberDeviceCookie: challengePostResponse.headers['set-cookie'].find((cookie) => cookie.includes('GRAVITEE_IO_REMEMBER_DEVICE')),
  };
}

export async function processLoginFromContext(ctx: TestSuiteContext, rememberDevice: boolean = false, devId?) {
  const authResponse = await get(ctx.clientAuthUrl, 302, ctx.session ? { Cookies: ctx.session } : {});
  const loginPage = await followUpGet(authResponse, 200);

  let xsrf = extractDomValue(loginPage, '[name=X-XSRF-TOKEN]');
  let action = extractDomAttr(loginPage, 'form', 'action');
  let cookies = loginPage.headers['set-cookie'];
  if (ctx.session?.rememberDeviceCookie) {
    cookies.push(ctx.session.rememberDeviceCookie);
  }
  const loginPostResponse = await postForm(
    action,
    {
      'X-XSRF-TOKEN': xsrf,
      username: ctx.user.username,
      password: ctx.user.password,
      rememberMe: 'off',
      client_id: ctx.client.clientId,
      ...(rememberDevice && {
        deviceId: devId,
        deviceType: 'MacOS',
      }),
    },
    {
      Cookie: cookies,
      'Content-type': 'application/x-www-form-urlencoded',
    },
    302,
  );
  return await followUpGet(loginPostResponse, 302);
}

export async function postMfaChallenge(ctx: TestSuiteContext, challengeResponse: any, code: any) {
  return await performPost(
    challengeResponse.action,
    '',
    {
      factorId: ctx.domain.domain.factors[0].id,
      code: code,
      'X-XSRF-TOKEN': challengeResponse.token,
    },
    {
      Cookie: challengeResponse.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);
}
