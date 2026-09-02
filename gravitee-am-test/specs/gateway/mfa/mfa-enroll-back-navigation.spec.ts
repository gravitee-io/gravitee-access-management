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
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import fetch from 'cross-fetch';
import { TOTP } from 'otpauth';
import { createDomain, deleteDomain, startDomain, waitForDomainStart } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { loginUserNameAndPassword, postConsent } from '@gateway-commands/login-commands';
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { createOtpFactor, createRequiredMfaApp } from './fixture/mfa-setup-fixture';
import { extractSharedSecret } from './fixture/mfa-extract-fixture';

const cheerio = require('cheerio');

global.fetch = fetch;
jest.setTimeout(200000);

let domain;
let accessToken;
let totpFactor;
let totpApp;
let openIdConfiguration;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  domain = await createDomain(accessToken, uniqueName('mfa-enroll-back', true), 'mfa enroll back navigation');
  totpFactor = await createOtpFactor(domain, accessToken);
  totpApp = await createRequiredMfaApp(domain, accessToken, [totpFactor.id]);

  const started = await startDomain(domain.id, accessToken).then(waitForDomainStart);
  domain = started.domain;
  openIdConfiguration = started.oidcConfig;
});

afterAll(async () => {
  if (domain?.id) {
    await deleteDomain(domain.id, accessToken);
  }
});

describe('MFA enroll back navigation (AM-7609)', () => {
  it('should keep the shared secret when the user goes back from the challenge page', async () => {
    const user = await buildCreateAndTestUser(domain.id, accessToken, 1);
    const clientId = totpApp.settings.oauth.clientId;

    const authorize = await loginUserNameAndPassword(clientId, user, user.password, false, openIdConfiguration, domain);
    expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/enroll`);

    const firstEnrollPage = await performGet(authorize.headers['location'], '', {
      Cookie: authorize.headers['set-cookie'],
    }).expect(200);
    const firstEnrollment = extractSharedSecret(firstEnrollPage, 'TOTP');
    expect(firstEnrollment.sharedSecret).toBeTruthy();

    const enrollPost = await performPost(
      firstEnrollment.action,
      '',
      {
        factorId: totpFactor.id,
        sharedSecret: firstEnrollment.sharedSecret,
        user_mfa_enrollment: true,
        'X-XSRF-TOKEN': firstEnrollment.token,
      },
      {
        Cookie: firstEnrollPage.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    const challengeRedirect = await performGet(enrollPost.headers['location'], '', {
      Cookie: enrollPost.headers['set-cookie'],
    }).expect(302);
    expect(challengeRedirect.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

    const challengePage = await performGet(challengeRedirect.headers['location'], '', {
      Cookie: challengeRedirect.headers['set-cookie'],
    }).expect(200);

    const backAction = cheerio.load(challengePage.text)('#backToEnroll').attr('href');
    expect(backAction).toBeDefined();

    const secondEnrollPage = await performGet(backAction, '', {
      Cookie: challengePage.headers['set-cookie'],
    }).expect(200);
    const secondEnrollment = extractSharedSecret(secondEnrollPage, 'TOTP');
    expect(secondEnrollment.sharedSecret).toEqual(firstEnrollment.sharedSecret);

    const secondEnrollPost = await performPost(
      secondEnrollment.action,
      '',
      {
        factorId: totpFactor.id,
        sharedSecret: secondEnrollment.sharedSecret,
        user_mfa_enrollment: true,
        'X-XSRF-TOKEN': secondEnrollment.token,
      },
      {
        Cookie: secondEnrollPage.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    const secondChallengeRedirect = await performGet(secondEnrollPost.headers['location'], '', {
      Cookie: secondEnrollPost.headers['set-cookie'],
    }).expect(302);
    const secondChallengePage = await performGet(secondChallengeRedirect.headers['location'], '', {
      Cookie: secondChallengeRedirect.headers['set-cookie'],
    }).expect(200);

    const dom = cheerio.load(secondChallengePage.text);
    const code = new TOTP({ issuer: 'Gravitee.io', secret: firstEnrollment.sharedSecret }).generate();
    const verified = await performPost(
      dom('form').attr('action'),
      '',
      {
        factorId: totpFactor.id,
        code: code,
        'X-XSRF-TOKEN': dom('[name=X-XSRF-TOKEN]').val(),
      },
      {
        Cookie: secondChallengePage.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    const consentRedirect = await performGet(verified.headers['location'], '', {
      Cookie: verified.headers['set-cookie'],
    }).expect(302);
    expect(consentRedirect.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/consent`);

    const consentPage = await performGet(consentRedirect.headers['location'], '', {
      Cookie: consentRedirect.headers['set-cookie'],
    }).expect(200);
    const consentPost = await postConsent(consentPage);

    const callback = await performGet(consentPost.headers['location'], '', {
      Cookie: consentPost.headers['set-cookie'],
    }).expect(302);
    expect(callback.headers['location']).toContain('code=');
  });
});
