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
import { ForgotPasswordContext, requestForgotPassword, retrieveEmailLinkForReset } from './fixture/forgot-password-flow-utils';
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
  inherited: true,
  settings: {
    accountSettings: {
      resetPasswordCustomForm: true,
      resetPasswordCustomFormFields: [
        {
          key: 'email',
          label: 'Email',
          type: 'email',
        },
        {
          key: 'username',
          label: 'Username',
          type: 'text',
        },
      ],
      resetPasswordConfirmIdentity: true,
      resetPasswordInvalidateTokens: true,
    },
  },
  passwordPolicy: {
    name: 'default',
    minLength: 5,
    maxLength: 64,
    includeNumbers: true,
    includeSpecialCharacters: true,
    lettersInMixedCase: true,
    maxConsecutiveLetters: 5,
    expiryDuration: 9,
    passwordHistoryEnabled: true,
    oldPasswords: 3,
  },
};

let resetPasswordContext: ResetPasswordContext;
let forgotPasswordContext: ForgotPasswordContext;
let fixture: ForgotPasswordFixture;
let confirmationLink: string;

beforeAll(async () => {
  fixture = await setupFixture(setting, userProps);
  forgotPasswordContext = {
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
  const passwordHistoryTests = [
    {
      password: 'SomeP@ssw0rd01',
      expectedMsg: resetPasswordFailed,
    },
    {
      password: 'SomeP@ssw0rd02',
      expectedMsg: 'success=reset_password_completed',
    },
    {
      password: 'SomeP@ssw0rd03',
      expectedMsg: 'success=reset_password_completed',
    },
    {
      password: 'SomeP@ssw0rd04',
      expectedMsg: 'success=reset_password_completed',
    },
    {
      password: 'SomeP@ssw0rd01',
      expectedMsg: 'success=reset_password_completed',
    },
  ];

  describe(`when password history is enabled for ${setting.passwordPolicy.oldPasswords} passwords`, () => {
    passwordHistoryTests.forEach(({ password, expectedMsg }) => {
      describe(`when resetting password with ${password}`, () => {
        it('should redirect to forgot password form', async () => {
          await requestForgotPassword(forgotPasswordContext, setting.settings);
        });

        it('should receive an email with a link to the reset password form', async () => {
          confirmationLink = await retrieveEmailLinkForReset(userProps.email);
        });

        it(`${password} should return ${expectedMsg}`, async () => {
          await resetPassword(confirmationLink, password, expectedMsg, setting.settings, resetPasswordContext);
        });
      });
    });
  });

  describe('when a Password Policy has been configured', () => {
    beforeAll(async () => {
      // Clear emails for this specific recipient at the start to avoid interference from other tests
      await clearEmails(userProps.email);
      await requestForgotPassword(forgotPasswordContext, setting.settings);
      confirmationLink = await retrieveEmailLinkForReset(userProps.email);
    });

    describe(`when a password is shorter than the minimum length of ${setting.passwordPolicy.minLength}`, () => {
      const minLength = 'SomeP@ssw0rd99'.substring(0, setting.passwordPolicy.minLength - 1);
      it(`reset password should fail with ${invalidPasswordValue}`, async () => {
        await resetPassword(confirmationLink, minLength, invalidPasswordValue, setting.settings, resetPasswordContext);
      });
    });

    describe(`when a password is longer than the maximum length of ${setting.passwordPolicy.maxLength}`, () => {
      let maxLength = 'SomeP@ssw0rd99';
      while (maxLength.length <= setting.passwordPolicy.maxLength) {
        maxLength += maxLength;
      }
      it(`reset password should fail with ${invalidPasswordValue}`, async () => {
        await resetPassword(confirmationLink, maxLength, invalidPasswordValue, setting.settings, resetPasswordContext);
      });
    });

    describe("when 'includeNumbers' is enabled and a password does not contain numbers", () => {
      it(`reset password should fail with ${invalidPasswordValue}`, async () => {
        await resetPassword(confirmationLink, 'SomeP@ssword', invalidPasswordValue, setting.settings, resetPasswordContext);
      });
    });

    describe("when 'includeSpecialCharacters' is enabled and a password does not contain special characters", () => {
      if (setting.passwordPolicy.includeSpecialCharacters) {
        it(`reset password should fail with ${invalidPasswordValue}`, async () => {
          await resetPassword(confirmationLink, 'SomePassw0rd99', invalidPasswordValue, setting.settings, resetPasswordContext);
        });
      }
    });

    describe("when 'lettersInMixedCase' is enabled and a password is not mixed case", () => {
      it(`reset password should fail with ${invalidPasswordValue}`, async () => {
        await resetPassword(confirmationLink, 'somepassw0rd99', invalidPasswordValue, setting.settings, resetPasswordContext);
      });
    });

    describe(`when password contains sequence exceeding ${setting.passwordPolicy.maxConsecutiveLetters} consecutive letters`, () => {
      it(`reset password should fail with ${invalidPasswordValue}`, async () => {
        let letters = '';
        for (let i = 0; i <= setting.passwordPolicy.maxConsecutiveLetters; i++) {
          letters += 'a';
        }
        let maxConsecutiveLetters = 'SomeP@ssw0rd99' + letters;
        await resetPassword(confirmationLink, maxConsecutiveLetters, invalidPasswordValue, setting.settings, resetPasswordContext);
      });
    });
  });
});
