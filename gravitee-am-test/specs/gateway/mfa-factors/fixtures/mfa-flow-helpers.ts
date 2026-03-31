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
import { extractXsrfTokenAndActionResponse, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { getLastEmail, clearEmails } from '@utils-commands/email-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { extractSharedSecret, extractSharedSecretForFactor } from '../../mfa/fixture/mfa-extract-fixture';

/** Creates a WEB app with required MFA enrollment + challenge for the given factor. */
export async function createMfaApplication(domainId: string, accessToken: string, factorId: string, namePrefix: string) {
  const idpSet = await getAllIdps(domainId, accessToken);
  const defaultIdp = idpSet.values().next().value;
  if (!defaultIdp) throw new Error('No default IdP found');

  const app = await createApplication(domainId, accessToken, {
    name: uniqueName(`${namePrefix}-app`, true),
    type: 'WEB',
    clientId: uniqueName(`${namePrefix}-client`, true),
    redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
  });
  return updateApplication(domainId, accessToken, {
    settings: {
      oauth: {
        redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
        grantTypes: ['authorization_code'],
        scopeSettings: [{ scope: 'openid', defaultScope: true }],
      },
      mfa: {
        factor: {
          defaultFactorId: factorId,
          applicationFactors: [{ id: factorId, selectionRule: '' }],
        },
        enroll: { active: true, type: 'REQUIRED', forceEnrollment: true },
        challenge: { active: true, type: 'REQUIRED' },
      },
    },
    identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
  }, app.id);
}

/** Enrols in an HTTP factor (no phone/QR — just factorId + submit). */
export const enrollHttpFactor = async (authResponse: any, factor: any) => {
  const enrollPage = await extractXsrfTokenAndActionResponse(authResponse);

  return performPost(
    enrollPage.action,
    '',
    {
      factorId: factor.id,
      sharedSecret: '',
      user_mfa_enrollment: true,
      'X-XSRF-TOKEN': enrollPage.token,
    },
    {
      Cookie: enrollPage.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  );
};

/** Enrols in a call factor with phone number submission. */
export const enrollCallFactor = async (authResponse: any, factor: any, phoneNumber = '+33780763733') => {
  const result = await performGet(authResponse.headers['location'], '', {
    Cookie: authResponse.headers['set-cookie'],
  }).expect(200);
  const extractedResult = extractSharedSecret(result, 'CALL');

  const enrollMfa = await performPost(
    extractedResult.action,
    '',
    {
      factorId: factor.id,
      sharedSecret: extractedResult.sharedSecret || '',
      user_mfa_enrollment: true,
      phone: phoneNumber,
      'X-XSRF-TOKEN': extractedResult.token,
    },
    {
      Cookie: result.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

  expect(enrollMfa.headers['location']).toContain('/oauth/authorize');
  return { headers: enrollMfa.headers };
};

/** Enrols in a TOTP-based factor (OTP sender, standard OTP). Returns `{ headers, sharedSecret }`. */
export const enrollTotpFactor = async (authResponse: any, factor: any, factorType: string = 'TOTP') => {
  const result = await performGet(authResponse.headers['location'], '', {
    Cookie: authResponse.headers['set-cookie'],
  }).expect(200);
  const extractedResult =
    factor?.id != null
      ? extractSharedSecretForFactor(result, factor, factorType)
      : extractSharedSecret(result, factorType);

  const enrollMfa = await performPost(
    extractedResult.action,
    '',
    {
      factorId: factor.id,
      sharedSecret: extractedResult.sharedSecret,
      user_mfa_enrollment: true,
      'X-XSRF-TOKEN': extractedResult.token,
    },
    {
      Cookie: result.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

  expect(enrollMfa.headers['location']).toContain('/oauth/authorize');
  return { headers: enrollMfa.headers, sharedSecret: extractedResult.sharedSecret };
};

/** Submits a verification code on the MFA challenge page. */
export const verifyFactor = async (challengeRedirect: any, code: string, factor: any) => {
  const challengeResponse = await extractXsrfTokenAndActionResponse(challengeRedirect);
  return performPost(
    challengeResponse.action,
    '',
    {
      factorId: factor.id,
      code,
      'X-XSRF-TOKEN': challengeResponse.token,
    },
    {
      Cookie: challengeResponse.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);
};

/**
 * Loads challenge page once, reads OTP from fakeSMTP, and submits it.
 * Must not GET the challenge URL twice — a second load sends a new code and invalidates the first.
 */
export const verifyMfaChallengeWithEmailOtpCode = async (
  challengeRedirect: any,
  factor: any,
  toEmail: string,
  emailPollMs = 5000,
) => {
  const challengeResponse = await extractXsrfTokenAndActionResponse(challengeRedirect);
  const email = await getLastEmail(emailPollMs, toEmail);
  expect(email).not.toBeNull();
  expect(email.contents).toEqual(expect.any(Array));
  const html = email.contents[0].data;
  const verificationMatch = html.match(/class="otp-code"[\s\S]*?<span[^>]*>\s*([0-9]{6})\s*<\/span>/i);
  expect(verificationMatch).not.toBeNull();
  const verificationCode = verificationMatch![1];
  await clearEmails(toEmail);
  return performPost(
    challengeResponse.action,
    '',
    {
      factorId: factor.id,
      code: verificationCode,
      'X-XSRF-TOKEN': challengeResponse.token,
    },
    {
      Cookie: challengeResponse.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);
};
