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
import { afterAll, beforeAll } from '@jest/globals';
import { ForgotPasswordFixture, setupFixture } from './fixture/forgot-password-fixture';
import { resetPassword, ResetPasswordContext } from './fixture/reset-password-flow-utils';
import { requestForgotPassword, retrieveEmailLinkForReset } from './fixture/forgot-password-flow-utils';
import { DomainTestSettings } from './fixture/settings-utils';
import { clearEmails } from '@utils-commands/email-commands';
import { setup } from '../../test-fixture';

const resetPasswordFailed = 'error=reset_password_failed';
const invalidPasswordValue = 'invalid_password_value';
const userProps = {
  firstName: 'firstName',
  lastName: 'lastName',
  email: 'test@mail.com',
  username: `test123`,
  password: 'SomeP@ssw0rd01',
};

setup(20000);

const setting: DomainTestSettings = {
  inherited: false,
  settings: {
    accountSettings: {
      inherited: false,
      autoLoginAfterResetPassword: true,
      redirectUriAfterResetPassword: 'http://localhost:4000',
    },
    passwordSettings: {
      inherited: false,
      minLength: 5,
      maxLength: 24,
      excludePasswordsInDictionary: true,
      excludeUserProfileInfoInPassword: true,
    },
  },
};

let resetPasswordContext: ResetPasswordContext;
let fixture: ForgotPasswordFixture;
let confirmationLink: string;

beforeAll(async () => {
  fixture = await setupFixture(setting, userProps);
  const forgotPasswordContext = {
    domainHrid: fixture.domain.hrid,
    clientId: fixture.clientId,
    openIdConfiguration: fixture.openIdConfiguration,
    user: fixture.user,
  };
  resetPasswordContext = {
    openIdConfiguration: fixture.openIdConfiguration,
    application: fixture.application,
    userSessionToken: fixture.userSessionToken,
    user: fixture.user,
    resetPasswordFailed,
    invalidPasswordValue,
  };
  // Clear emails for this specific recipient at the start to avoid interference from other tests
  await clearEmails(userProps.email);
  await requestForgotPassword(forgotPasswordContext, setting.settings);
  confirmationLink = await retrieveEmailLinkForReset(userProps.email);
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Gateway reset password', () => {
  describe('when reset password succeeds with a valid password', () => {
    it('should redirect to redirectUriAfterResetPassword with auto-login and old token remains active', async () => {
      // Re-request forgot password to get a fresh confirmation link (the one from beforeAll may have been consumed by policy tests)
      await clearEmails(userProps.email);
      await requestForgotPassword(
        {
          domainHrid: fixture.domain.hrid,
          clientId: fixture.clientId,
          openIdConfiguration: fixture.openIdConfiguration,
          user: fixture.user,
        },
        setting.settings,
      );
      const freshConfirmationLink = await retrieveEmailLinkForReset(userProps.email);
      // Password must pass: min 5, max 24, not in dictionary, not user profile info
      const validPassword = 'V@l1dNewP@ss99';
      await resetPassword(
        freshConfirmationLink,
        validPassword,
        setting.settings.accountSettings.redirectUriAfterResetPassword,
        setting.settings,
        resetPasswordContext,
      );
    });
  });

  describe('when a Password Policy has been configured', () => {
    describe(`when a password is shorter than the minimum length of ${setting.settings.passwordSettings.minLength}`, () => {
      const minLength = 'SomeP@ssw0rd99'.substring(0, setting.settings.passwordSettings.minLength - 1);
      it(`reset password should fail with ${invalidPasswordValue}`, async () => {
        await resetPassword(confirmationLink, minLength, invalidPasswordValue, setting.settings, resetPasswordContext);
      });
    });

    describe(`when a password is longer than the maximum length of ${setting.settings.passwordSettings.maxLength}`, () => {
      let maxLength = 'SomeP@ssw0rd99';
      while (maxLength.length <= setting.settings.passwordSettings.maxLength) {
        maxLength += maxLength;
      }
      it(`reset password should fail with ${invalidPasswordValue}`, async () => {
        await resetPassword(confirmationLink, maxLength, invalidPasswordValue, setting.settings, resetPasswordContext);
      });
    });

    ['password', 'passw0rd', 'pass123', 'passwd', 'gravity'].forEach((password) => {
      describe(`when 'excludePasswordsInDictionary' is enabled and '${password}' from the dictionary is used`, () => {
        it(`reset password should fail with ${invalidPasswordValue}`, async () => {
          await resetPassword(confirmationLink, password, invalidPasswordValue, setting.settings, resetPasswordContext);
        });
      });
    });

    Object.entries(userProps)
      .filter(([key]) => key !== 'password')
      .forEach(([propName, value]) => {
        describe(`when 'excludeUserProfileInfoInPassword' is enabled and a user's '${propName}' is used`, () => {
          it(`reset password should fail with ${invalidPasswordValue}`, async () => {
            await resetPassword(confirmationLink, value, invalidPasswordValue, setting.settings, resetPasswordContext);
          });
        });
      });
  });
});
