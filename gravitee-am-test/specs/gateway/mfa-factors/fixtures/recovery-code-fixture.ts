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
import * as cheerio from 'cheerio';
import {
  createDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createFactor } from '@management-commands/factor-management-commands';
import { buildCreateAndTestUser, deleteUser } from '@management-commands/user-management-commands';
import {
  extractXsrfTokenAndActionResponse,
  performGet,
  performPost,
  requestToken,
} from '@gateway-commands/oauth-oidc-commands';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

const REDIRECT_URI = 'https://auth-nightly.gravitee.io/myApp/callback';

/** The code the mock factor always accepts, so the usual method never needs a real device. */
const MOCK_FACTOR_CODE = '123456';

/**
 * How many recovery codes a user is issued.
 *
 * The factor's own configuration decides this, so exhausting a user's codes costs three sign-ins
 * rather than the ten a default set would need.
 */
export const RECOVERY_CODE_COUNT = 2;

/** Digits per code. Kept away from the mock factor's code so the two can never be confused. */
const RECOVERY_CODE_DIGITS = 5;

export interface RecoveryCodeUser {
  user: any;
  /** Every code the user was issued, in the order the page listed them. */
  codes: string[];
  /**
   * The user's own access token, earned by the enrolment sign-in.
   *
   * That sign-in answers the mock factor, so obtaining it costs no recovery code and the tests
   * can read the user's remaining codes without changing them.
   */
  accessToken: string;
}

export interface RecoveryCodeFixture extends Fixture {
  accessToken: string;
  domain: any;
  openIdConfiguration: any;
  clientId: string;
  /** A second application in the same domain, for a retry the MFA rate limiter cannot refuse. */
  secondClientId: string;
  recoveryFactor: any;
  /**
   * Creates a user, signs them in, enrols them in the mock factor, answers the challenge and
   * returns them with the recovery codes they were shown.
   *
   * Each test takes its own user: codes are held per user, so a test that consumes one cannot
   * change what another test sees.
   */
  enrolUserWithRecoveryCodes: () => Promise<RecoveryCodeUser>;
  /**
   * Signs in as far as the recovery code challenge and submits `code`.
   *
   * Returns the gateway's response without asserting on it — whether a code is accepted is what
   * the tests are here to establish, so the helper must not decide it.
   */
  submitRecoveryCode: (user: any, code: string, useSecondApp?: boolean) => Promise<any>;
  /**
   * Finishes a sign-in that the gateway accepted and returns the user's own access token.
   *
   * The token comes from the authorization code that sign-in produced, so it is earned by
   * passing the recovery code challenge rather than by any route that skips MFA.
   */
  completeSignIn: (acceptedResponse: any, useSecondApp?: boolean) => Promise<string>;
  /** The recovery codes still valid for the user holding this token. */
  remainingCodes: (userAccessToken: string) => Promise<string[]>;
  /** Replaces the user's recovery codes with a fresh set and returns it. */
  regenerateCodes: (userAccessToken: string) => Promise<string[]>;
}

/** Every code listed on the recovery codes page, not just the first. */
const readRecoveryCodes = (html: string): string[] => {
  const $ = cheerio.load(html);
  const codes = $('.code-item')
    .map((_, el) => $(el).text().trim())
    .get()
    .filter((c) => c.length > 0);
  if (codes.length === 0) {
    throw new Error('No recovery codes found on the page');
  }
  return codes;
};

export const setupRecoveryCodeFixture = async (): Promise<RecoveryCodeFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: any = null;

  try {
    domain = await createDomain(accessToken, uniqueName('recovery-code', true), 'AM-2216 recovery code reuse');

    // The account API is how a user sees which of their recovery codes are still valid, and it is
    // served only when self-service account management is on for the domain.
    await patchDomain(domain.id, accessToken, {
      selfServiceAccountManagementSettings: { enabled: true },
    } as any);

    const mockFactor = await createFactor(domain.id, accessToken, {
      type: 'mock-am-factor',
      factorType: 'MOCK',
      configuration: JSON.stringify({ code: MOCK_FACTOR_CODE }),
      name: 'Mock Factor',
    });

    const recoveryFactor = await createFactor(domain.id, accessToken, {
      type: 'recovery-code-am-factor',
      factorType: 'Recovery Code',
      configuration: JSON.stringify({ digit: RECOVERY_CODE_DIGITS, count: RECOVERY_CODE_COUNT }),
      name: 'Recovery Code',
    });

    const idpSet = await getAllIdps(domain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;
    if (!defaultIdp) {
      throw new Error('Domain has no default identity provider');
    }

    // The secret is set here rather than left to AM: the update call's response does not carry it
    // back, and the token exchange below needs it to authenticate the client.
    const buildApp = async () => {
      const clientSecret = uniqueName('recovery-secret', true);
      const created = await createApplication(domain.id, accessToken, {
        name: uniqueName('recovery-app', true),
        type: 'WEB',
        clientId: uniqueName('recovery-client', true),
        clientSecret,
        redirectUris: [REDIRECT_URI],
      });
      return { created, clientSecret };
    };
    const first = await buildApp();
    const second = await buildApp();

    // Enrolment and challenge are both required, so every sign-in reaches the factor step and
    // the recovery code is genuinely the thing being exercised.
    const applyMfaSettings = async (appId: string) => updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: [REDIRECT_URI],
            grantTypes: ['authorization_code'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
          mfa: {
            factor: {
              defaultFactorId: mockFactor.id,
              applicationFactors: [
                { id: mockFactor.id, selectionRule: '' },
                { id: recoveryFactor.id, selectionRule: '' },
              ],
            },
            enroll: { active: true, type: 'REQUIRED', forceEnrollment: true },
            challenge: { active: true, type: 'REQUIRED' },
          },
          // The consent page is not part of what these tests exercise, and skipping it lets a
          // sign-in run through to the token the account API needs.
          advanced: { skipConsent: true },
        },
        identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
      } as any,
      appId,
    );
    const withSecret = (app: any, clientSecret: string) => ({
      ...app,
      settings: { ...app.settings, oauth: { ...app.settings.oauth, clientSecret } },
    });
    const application = withSecret(await applyMfaSettings(first.created.id), first.clientSecret);
    const secondApplication = withSecret(await applyMfaSettings(second.created.id), second.clientSecret);

    await startDomain(domain.id, accessToken);
    const started = await waitForDomainStart(domain);
    const openIdConfiguration = started.oidcConfig;
    const clientId = application.settings.oauth.clientId;
    const secondClientId = secondApplication.settings.oauth.clientId;
    const createdUserIds: string[] = [];

    const enrolUserWithRecoveryCodes = async (): Promise<RecoveryCodeUser> => {
      const user = await buildCreateAndTestUser(domain.id, accessToken, createdUserIds.length + 1);
      createdUserIds.push(user.id);

      const loginResponse = await loginUserNameAndPassword(clientId, user, user.password, false, openIdConfiguration, domain);
      expect(loginResponse.headers['location']).toContain(`/${domain.hrid}/mfa/enroll`);

      // Enrol in the mock factor: recovery codes are issued alongside a real factor, never alone.
      const enrolPage = await extractXsrfTokenAndActionResponse(loginResponse);
      const enrolled = await performPost(
        enrolPage.action,
        '',
        { factorId: mockFactor.id, user_mfa_enrollment: true, 'X-XSRF-TOKEN': enrolPage.token },
        { Cookie: enrolPage.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
      ).expect(302);

      const challengeRedirect = await performGet(enrolled.headers['location'], '', {
        Cookie: enrolled.headers['set-cookie'],
      }).expect(302);
      expect(challengeRedirect.headers['location']).toContain(`/${domain.hrid}/mfa/challenge`);

      const challengePage = await extractXsrfTokenAndActionResponse(challengeRedirect);
      const verified = await performPost(
        challengePage.action,
        '',
        { factorId: mockFactor.id, code: MOCK_FACTOR_CODE, 'X-XSRF-TOKEN': challengePage.token },
        { Cookie: challengePage.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
      ).expect(302);

      // Answering the first challenge is what triggers the recovery codes page.
      const toRecoveryCodes = await performGet(verified.headers['location'], '', {
        Cookie: verified.headers['set-cookie'],
      }).expect(302);

      const recoveryCodesPage = await performGet(toRecoveryCodes.headers['location'], '', {
        Cookie: toRecoveryCodes.headers['set-cookie'],
      }).expect(200);

      const codes = readRecoveryCodes(recoveryCodesPage.text);

      // Acknowledge the page so the sign-in finishes and the codes are stored against the user.
      const $ = cheerio.load(recoveryCodesPage.text);
      const acknowledged = await performPost(
        toRecoveryCodes.headers['location'],
        '',
        { 'X-XSRF-TOKEN': $('[name=X-XSRF-TOKEN]').val() as string },
        { Cookie: recoveryCodesPage.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
      ).expect(302);

      return { user, codes, accessToken: await completeSignIn(acknowledged) };
    };

    const submitRecoveryCode = async (user: any, code: string, useSecondApp = false) => {
      const app = useSecondApp ? secondClientId : clientId;
      const loginResponse = await loginUserNameAndPassword(app, user, user.password, false, openIdConfiguration, domain);
      expect(loginResponse.headers['location']).toContain(`/${domain.hrid}/mfa/challenge`);

      // A rate-limit redirect here means the caller reused an application whose bucket is spent,
      // not that anything is wrong with the code — fail loudly rather than test the limiter.
      const challengePage = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      });
      if (challengePage.status !== 200) {
        throw new Error(`Expected the challenge page, got ${challengePage.status} -> ${challengePage.headers['location']}`);
      }

      // "Try another way" — the link off the usual factor's challenge page.
      const alternativeUrl = cheerio.load(challengePage.text)('a').attr('href');
      if (!alternativeUrl) {
        throw new Error('No alternative factor link on the challenge page');
      }

      const alternativePage = cheerio.load(challengePage.text);
      const recoveryChallenge = await performPost(
        alternativeUrl,
        '',
        { factorId: recoveryFactor.id, 'X-XSRF-TOKEN': alternativePage('[name=X-XSRF-TOKEN]').val() as string },
        { Cookie: challengePage.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
      ).expect(302);

      const recoveryPage = await extractXsrfTokenAndActionResponse(recoveryChallenge);
      return performPost(
        recoveryPage.action,
        '',
        { factorId: recoveryFactor.id, code, 'X-XSRF-TOKEN': recoveryPage.token },
        { Cookie: recoveryPage.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
      );
    };

    const accountUrl = `${process.env.AM_GATEWAY_URL}/${domain.hrid}/account/api/auth/recovery_code`;

    const completeSignIn = async (acceptedResponse: any, useSecondApp = false): Promise<string> => {
      const app = useSecondApp ? secondApplication : application;
      const callback = await performGet(acceptedResponse.headers['location'], '', {
        Cookie: acceptedResponse.headers['set-cookie'],
      }).expect(302);
      const tokenResponse = await requestToken(app, openIdConfiguration, callback);
      return tokenResponse.body.access_token;
    };

    const remainingCodes = async (userAccessToken: string): Promise<string[]> => {
      const res = await performGet(accountUrl, '', { Authorization: `Bearer ${userAccessToken}` }).expect(200);
      return res.body;
    };

    const regenerateCodes = async (userAccessToken: string): Promise<string[]> => {
      const res = await performPost(accountUrl, '', null, { Authorization: `Bearer ${userAccessToken}` }).expect(200);
      return res.body;
    };

    return {
      accessToken,
      domain: started.domain,
      openIdConfiguration,
      clientId,
      secondClientId,
      recoveryFactor,
      enrolUserWithRecoveryCodes,
      submitRecoveryCode,
      completeSignIn,
      remainingCodes,
      regenerateCodes,
      cleanUp: async () => {
        for (const id of createdUserIds) {
          try {
            await deleteUser(domain.id, accessToken, id);
          } catch {
            // best effort — the domain delete below removes them anyway
          }
        }
        await safeDeleteDomain(domain.id, accessToken);
      },
    };
  } catch (error) {
    if (domain?.id) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (e) {
        console.error('Cleanup failed:', e);
      }
    }
    throw error;
  }
};
