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

import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { setup } from '../../test-fixture';
import { FeatureSettingsFixture, initFixture } from './fixtures/feature-settings-fixture';

setup();

let fixture: FeatureSettingsFixture;

beforeAll(async () => {
  fixture = await initFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Domain and Application Feature Settings', () => {
  describe('Domain login settings', () => {
    it('should enable user registration', async () => {
      const patched = await fixture.patchDomainSettings({ loginSettings: { registerEnabled: true } });
      expect(patched.loginSettings.registerEnabled).toBe(true);
    });

    it('should disable user registration', async () => {
      const patched = await fixture.patchDomainSettings({ loginSettings: { registerEnabled: false } });
      expect(patched.loginSettings.registerEnabled).toBe(false);
    });

    it('should persist user registration setting across GET', async () => {
      await fixture.patchDomainSettings({ loginSettings: { registerEnabled: true } });
      const fetched = await fixture.fetchDomain();
      expect(fetched.loginSettings.registerEnabled).toBe(true);
    });

    it('should enable identifier-first login', async () => {
      const patched = await fixture.patchDomainSettings({ loginSettings: { identifierFirstEnabled: true } });
      expect(patched.loginSettings.identifierFirstEnabled).toBe(true);
    });

    it('should disable identifier-first login', async () => {
      const patched = await fixture.patchDomainSettings({ loginSettings: { identifierFirstEnabled: false } });
      expect(patched.loginSettings.identifierFirstEnabled).toBe(false);
    });

    it('should enable hide login form', async () => {
      const patched = await fixture.patchDomainSettings({ loginSettings: { hideForm: true } });
      expect(patched.loginSettings.hideForm).toBe(true);
    });

    it('should disable hide login form', async () => {
      const patched = await fixture.patchDomainSettings({ loginSettings: { hideForm: false } });
      expect(patched.loginSettings.hideForm).toBe(false);
    });
  });

  describe('Application login settings — inherited from domain', () => {
    it('should default to inheriting domain login settings', async () => {
      const fetched = await fixture.fetchApp();
      expect(fetched.settings?.login?.inherited).not.toBe(false);
    });

    it('should reflect domain login settings when application is set to inherited', async () => {
      await fixture.patchDomainSettings({ loginSettings: { registerEnabled: true, forgotPasswordEnabled: true } });
      await fixture.patchApp({ settings: { login: { inherited: true } } });

      const fetched = await fixture.fetchApp();
      expect(fetched.settings.login.inherited).toBe(true);
    });
  });

  describe('Application login settings — application override', () => {
    it('should override login settings at application level when not inherited', async () => {
      const patched = await fixture.patchApp({
        settings: {
          login: {
            inherited: false,
            registerEnabled: false,
            forgotPasswordEnabled: false,
          },
        },
      });
      expect(patched.settings.login.inherited).toBe(false);
      expect(patched.settings.login.registerEnabled).toBe(false);
      expect(patched.settings.login.forgotPasswordEnabled).toBe(false);
    });

    it('should persist application login settings override across GET', async () => {
      await fixture.patchApp({
        settings: {
          login: {
            inherited: false,
            registerEnabled: true,
            forgotPasswordEnabled: false,
          },
        },
      });
      const fetched = await fixture.fetchApp();
      expect(fetched.settings.login.inherited).toBe(false);
      expect(fetched.settings.login.registerEnabled).toBe(true);
      expect(fetched.settings.login.forgotPasswordEnabled).toBe(false);
    });

    it('should revert to inherited when switching application login settings back to inherited', async () => {
      await fixture.patchApp({ settings: { login: { inherited: false, registerEnabled: false } } });
      const patched = await fixture.patchApp({ settings: { login: { inherited: true } } });
      expect(patched.settings.login.inherited).toBe(true);
    });
  });

  describe('Application account settings — inherited from domain', () => {
    it('should default to inheriting domain account settings', async () => {
      const fetched = await fixture.fetchApp();
      expect(fetched.settings?.account?.inherited).not.toBe(false);
    });

    it('should reflect inherited state when explicitly set to inherited', async () => {
      const patched = await fixture.patchApp({ settings: { account: { inherited: true } } });
      expect(patched.settings.account.inherited).toBe(true);
    });
  });

  describe('Application account settings — application override', () => {
    it('should override account settings at application level when not inherited', async () => {
      const patched = await fixture.patchApp({
        settings: {
          account: {
            inherited: false,
            loginAttemptsDetectionEnabled: true,
            maxLoginAttempts: 5,
            loginAttemptsResetTime: 60,
            accountBlockedDuration: 30,
          },
        },
      });
      expect(patched.settings.account.inherited).toBe(false);
      expect(patched.settings.account.loginAttemptsDetectionEnabled).toBe(true);
      expect(patched.settings.account.maxLoginAttempts).toBe(5);
      expect(patched.settings.account.loginAttemptsResetTime).toBe(60);
      expect(patched.settings.account.accountBlockedDuration).toBe(30);
    });

    it('should persist application account settings override across GET', async () => {
      await fixture.patchApp({
        settings: {
          account: {
            inherited: false,
            loginAttemptsDetectionEnabled: true,
            maxLoginAttempts: 3,
            loginAttemptsResetTime: 120,
            accountBlockedDuration: 60,
          },
        },
      });
      const fetched = await fixture.fetchApp();
      expect(fetched.settings.account.inherited).toBe(false);
      expect(fetched.settings.account.loginAttemptsDetectionEnabled).toBe(true);
      expect(fetched.settings.account.maxLoginAttempts).toBe(3);
    });

    it('should revert to inherited when switching application account settings back to inherited', async () => {
      await fixture.patchApp({ settings: { account: { inherited: false, loginAttemptsDetectionEnabled: true } } });
      const patched = await fixture.patchApp({ settings: { account: { inherited: true } } });
      expect(patched.settings.account.inherited).toBe(true);
    });
  });
});
