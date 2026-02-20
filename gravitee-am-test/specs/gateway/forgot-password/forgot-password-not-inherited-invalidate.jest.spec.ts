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
import { resetPassword } from './fixture/reset-password-flow-utils';
import { requestForgotPassword, retrieveEmailLinkForReset } from './fixture/forgot-password-flow-utils';
import { DomainTestSettings } from './fixture/settings-utils';
import { clearEmails } from '@utils-commands/email-commands';
import { setup } from '../../test-fixture';
import { uniqueName } from '@utils-commands/misc';

const userProps = {
  firstName: 'firstName',
  lastName: 'lastName',
  email: uniqueName('fp-nii', true) + '@mail.com',
  username: uniqueName('fp-nii', true),
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
      resetPasswordInvalidateTokens: true,
    },
    passwordSettings: {
      inherited: false,
      minLength: 5,
      maxLength: 24,
    },
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

describe('Gateway reset password with token invalidation (non-inherited)', () => {
  it('should auto-login with redirect and invalidate old token after successful reset', async () => {
    await clearEmails(userProps.email);
    await requestForgotPassword(fixture.forgotPasswordContext(), setting.settings);
    const freshConfirmationLink = await retrieveEmailLinkForReset(userProps.email);
    const validPassword = 'V@l1dNewP@ss99';
    await resetPassword(
      freshConfirmationLink,
      validPassword,
      setting.settings.accountSettings.redirectUriAfterResetPassword,
      setting.settings,
      fixture.resetPasswordContext(),
    );
  });
});
