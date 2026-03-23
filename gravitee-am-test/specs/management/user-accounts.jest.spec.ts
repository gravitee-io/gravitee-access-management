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
import { afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, patchDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../test-fixture';

let accessToken;
let domain;

setup(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  let startedDomain = await setupDomainForTest(uniqueName('user-accounts-test', true), { accessToken, waitForStart: true });
  domain = startedDomain.domain;
});

describe('User Accounts', () => {
  it('should persist remember me, login attempts and MFA alert settings when enabled', async () => {
    const patchedDomain = await patchDomain(domain.id, accessToken, {
      path: `${domain.path}`,
      vhostMode: false,
      vhosts: [],
      accountSettings: {
        inherited: false,
        rememberMe: true,
        rememberMeDuration: 10,
        loginAttemptsDetectionEnabled: true,
        maxLoginAttempts: 3,
        loginAttemptsResetTime: 120,
        accountBlockedDuration: 60,
        sendRecoverAccountEmail: true,
        useBotDetection: true,
        botDetectionPlugin: 'google-recaptcha-v3-am-bot-detection',
        deletePasswordlessDevicesAfterResetPassword: true,
        mfaChallengeAttemptsDetectionEnabled: true,
        mfaChallengeMaxAttempts: 2,
        mfaChallengeAttemptsResetTime: 30,
        mfaChallengeSendVerifyAlertEmail: true,
      },
    });

    const accountSettings = patchedDomain.accountSettings;
    expect(accountSettings.inherited).toBe(false);
    expect(accountSettings.rememberMe).toBe(true);
    expect(accountSettings.rememberMeDuration).toBe(10);
    expect(accountSettings.loginAttemptsDetectionEnabled).toBe(true);
    expect(accountSettings.maxLoginAttempts).toBe(3);
    expect(accountSettings.loginAttemptsResetTime).toBe(120);
    expect(accountSettings.accountBlockedDuration).toBe(60);
    expect(accountSettings.sendRecoverAccountEmail).toBe(true);
    expect(accountSettings.useBotDetection).toBe(true);
    expect(accountSettings.botDetectionPlugin).toBe('google-recaptcha-v3-am-bot-detection');
    expect(accountSettings.deletePasswordlessDevicesAfterResetPassword).toBe(true);
    expect(accountSettings.mfaChallengeAttemptsDetectionEnabled).toBe(true);
    expect(accountSettings.mfaChallengeMaxAttempts).toBe(2);
    expect(accountSettings.mfaChallengeAttemptsResetTime).toBe(30);
    expect(accountSettings.mfaChallengeSendVerifyAlertEmail).toBe(true);
  });

  it('should persist remember me, login attempts and MFA alert settings when disabled', async () => {
    const patchedDomain = await patchDomain(domain.id, accessToken, {
      path: `${domain.path}`,
      vhostMode: false,
      vhosts: [],
      accountSettings: {
        inherited: false,
        rememberMe: false,
        rememberMeDuration: 10,
        loginAttemptsDetectionEnabled: false,
        maxLoginAttempts: 3,
        loginAttemptsResetTime: 120,
        accountBlockedDuration: 60,
        sendRecoverAccountEmail: false,
        useBotDetection: false,
        deletePasswordlessDevicesAfterResetPassword: false,
        mfaChallengeAttemptsDetectionEnabled: false,
        mfaChallengeMaxAttempts: 2,
        mfaChallengeAttemptsResetTime: 30,
        mfaChallengeSendVerifyAlertEmail: false,
      },
    });

    const accountSettings = patchedDomain.accountSettings;
    expect(accountSettings.inherited).toBe(false);
    expect(accountSettings.rememberMe).toBe(false);
    expect(accountSettings.rememberMeDuration).toBe(10);
    expect(accountSettings.loginAttemptsDetectionEnabled).toBe(false);
    expect(accountSettings.maxLoginAttempts).toBe(3);
    expect(accountSettings.loginAttemptsResetTime).toBe(120);
    expect(accountSettings.accountBlockedDuration).toBe(60);
    expect(accountSettings.sendRecoverAccountEmail).toBe(false);
    expect(accountSettings.useBotDetection).toBe(false);
    expect(accountSettings.deletePasswordlessDevicesAfterResetPassword).toBe(false);
    expect(accountSettings.mfaChallengeAttemptsDetectionEnabled).toBe(false);
    expect(accountSettings.mfaChallengeMaxAttempts).toBe(2);
    expect(accountSettings.mfaChallengeAttemptsResetTime).toBe(30);
    expect(accountSettings.mfaChallengeSendVerifyAlertEmail).toBe(false);
  });
});

afterAll(async () => {
  await safeDeleteDomain(domain?.id, accessToken);
});
