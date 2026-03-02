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
  email: uniqueName('fp-cfnc', true) + '@mail.com',
  username: uniqueName('fp-cfnc', true),
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
      resetPasswordConfirmIdentity: false,
    },
  },
  passwordPolicy: {
    name: 'default',
    minLength: 5,
    maxLength: 64,
  },
};

let fixture: ForgotPasswordFixture;
let confirmationLink: string;

beforeAll(async () => {
  fixture = await setupFixture(setting, userProps);
  await clearEmails(userProps.email);
  // requestForgotPassword will verify custom form fields render (username + email) when resetPasswordCustomForm=true and resetPasswordConfirmIdentity=false
  await requestForgotPassword(fixture.forgotPasswordContext(), setting.settings);
  confirmationLink = await retrieveEmailLinkForReset(userProps.email);
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Gateway reset password with custom form (no confirm identity)', () => {
  it('should complete reset password successfully after custom form renders', async () => {
    const validPassword = 'V@l1dNewP@ss99';
    await resetPassword(confirmationLink, validPassword, 'success=reset_password_completed', setting.settings, fixture.resetPasswordContext());
  });
});
