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
import { performFormPost, performGet } from '@gateway-commands/oauth-oidc-commands';
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

export async function processMfaEnrollment(ctx: TestSuiteContext) {
  const authResponse = await get(ctx.clientAuthUrl, 302);
  const loginPage = await followUpGet(authResponse, 200);

  let xsrf = extractDomValue(loginPage, '[name=X-XSRF-TOKEN]');
  let action = extractDomAttr(loginPage, 'form', 'action');

  const loginPostResponse = await postForm(
    action,
    {
      'X-XSRF-TOKEN': xsrf,
      username: ctx.user.username,
      password: ctx.user.password,
      client_id: ctx.client.clientId,
    },
    {
      Cookie: loginPage.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
    302,
  );

  const enrollLocationResponse = await followUpGet(loginPostResponse, 302);
  const enrollmentPage = await followUpGet(enrollLocationResponse, 200);

  xsrf = extractDomValue(enrollmentPage, '[name=X-XSRF-TOKEN]');
  action = extractDomAttr(enrollmentPage, 'form', 'action');
  let factorId = extractDomValue(enrollmentPage, '[name=factorId]');

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

export async function processMfaEndToEnd(ctx: TestSuiteContext, rememberDevice: boolean = false) {
  const enrollmentPostResponse = await processMfaEnrollment(ctx);

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
  };
}
