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
import { afterAll, beforeAll, beforeEach } from '@jest/globals';
import { ForgotPasswordFixture, setupFixture } from './fixture/forgot-password-fixture';
import { resetPassword } from './fixture/reset-password-flow-utils';
import { requestForgotPassword, retrieveEmailLinkForReset } from './fixture/forgot-password-flow-utils';
import { DomainTestSettings } from './fixture/settings-utils';
import { clearEmails } from '@utils-commands/email-commands';
import { setup } from '../../test-fixture';
import { uniqueName } from '@utils-commands/misc';

const resetPasswordFailed = 'error=reset_password_failed';
const invalidPasswordValue = 'invalid_password_value';
const userProps = {
  firstName: 'firstName',
  lastName: 'lastName',
  email: uniqueName('fp-inh', true) + '@mail.com',
  username: uniqueName('fp-inh', true),
  password: 'SomeP@ssw0rd01',
};

setup(20000);

const setting: DomainTestSettings = {
  inherited: true,
  settings: {
    accountSettings: {
      deletePasswordlessDevicesAfterResetPassword: true,
      autoLoginAfterResetPassword: true,
      redirectUriAfterResetPassword: 'http://localhost:4000',
    },
  },
  passwordPolicy: {
    name: 'default',
    minLength: 5,
    maxLength: 24,
    includeNumbers: true,
    includeSpecialCharacters: true,
    lettersInMixedCase: true,
    maxConsecutiveLetters: 5,
    expiryDuration: 9,
    passwordHistoryEnabled: true,
    oldPasswords: 3,
  },
};

let fixture: ForgotPasswordFixture;

beforeAll(async () => {
  fixture = await setupFixture(setting, userProps);
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Gateway reset password', () => {
  const passwordHistoryTests = [
    {
      password: 'SomeP@ssw0rd01',
      expectedMsg: resetPasswordFailed,
    },
    {
      password: 'SomeP@ssw0rd02',
      expectedMsg: setting.settings.accountSettings.redirectUriAfterResetPassword,
    },
    {
      password: 'SomeP@ssw0rd03',
      expectedMsg: setting.settings.accountSettings.redirectUriAfterResetPassword,
    },
    {
      password: 'SomeP@ssw0rd04',
      expectedMsg: setting.settings.accountSettings.redirectUriAfterResetPassword,
    },
    {
      password: 'SomeP@ssw0rd01',
      expectedMsg: setting.settings.accountSettings.redirectUriAfterResetPassword,
    },
  ];

  describe(`when password history is enabled for ${setting.passwordPolicy.oldPasswords} passwords`, () => {
    passwordHistoryTests.forEach(({ password, expectedMsg }) => {
      it(`resetting with ${password} should return ${expectedMsg}`, async () => {
        await clearEmails(userProps.email);
        await requestForgotPassword(fixture.forgotPasswordContext(), setting.settings);
        const link = await retrieveEmailLinkForReset(userProps.email);
        await resetPassword(link, password, expectedMsg, setting.settings, fixture.resetPasswordContext());
      });
    });
  });

  describe('when a Password Policy has been configured', () => {
    let confirmationLink: string;

    beforeEach(async () => {
      await clearEmails(userProps.email);
      await requestForgotPassword(fixture.forgotPasswordContext(), setting.settings);
      confirmationLink = await retrieveEmailLinkForReset(userProps.email);
    });

    describe(`when a password is shorter than the minimum length of ${setting.passwordPolicy.minLength}`, () => {
      const minLength = 'SomeP@ssw0rd99'.substring(0, setting.passwordPolicy.minLength - 1);
      it(`reset password should fail with ${invalidPasswordValue}`, async () => {
        await resetPassword(confirmationLink, minLength, invalidPasswordValue, setting.settings, fixture.resetPasswordContext());
      });
    });

    describe(`when a password is longer than the maximum length of ${setting.passwordPolicy.maxLength}`, () => {
      let maxLength = 'SomeP@ssw0rd99';
      while (maxLength.length <= setting.passwordPolicy.maxLength) {
        maxLength += maxLength;
      }
      it(`reset password should fail with ${invalidPasswordValue}`, async () => {
        await resetPassword(confirmationLink, maxLength, invalidPasswordValue, setting.settings, fixture.resetPasswordContext());
      });
    });

    describe("when 'includeNumbers' is enabled and a password does not contain numbers", () => {
      it(`reset password should fail with ${invalidPasswordValue}`, async () => {
        await resetPassword(confirmationLink, 'SomeP@ssword', invalidPasswordValue, setting.settings, fixture.resetPasswordContext());
      });
    });

    describe("when 'includeSpecialCharacters' is enabled and a password does not contain special characters", () => {
      if (setting.passwordPolicy.includeSpecialCharacters) {
        it(`reset password should fail with ${invalidPasswordValue}`, async () => {
          await resetPassword(confirmationLink, 'SomePassw0rd99', invalidPasswordValue, setting.settings, fixture.resetPasswordContext());
        });
      }
    });

    describe("when 'lettersInMixedCase' is enabled and a password is not mixed case", () => {
      it(`reset password should fail with ${invalidPasswordValue}`, async () => {
        await resetPassword(confirmationLink, 'somepassw0rd99', invalidPasswordValue, setting.settings, fixture.resetPasswordContext());
      });
    });

    describe(`when password contains sequence exceeding ${setting.passwordPolicy.maxConsecutiveLetters} consecutive letters`, () => {
      it(`reset password should fail with ${invalidPasswordValue}`, async () => {
        let letters = '';
        for (let i = 0; i <= setting.passwordPolicy.maxConsecutiveLetters; i++) {
          letters += 'a';
        }
        let maxConsecutiveLetters = 'SomeP@ssw0rd99' + letters;
        await resetPassword(confirmationLink, maxConsecutiveLetters, invalidPasswordValue, setting.settings, fixture.resetPasswordContext());
      });
    });
  });
});
