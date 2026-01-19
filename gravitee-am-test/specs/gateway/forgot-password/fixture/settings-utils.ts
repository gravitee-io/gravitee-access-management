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

import { AccountSettings } from '@management-models/AccountSettings';
import { PasswordSettings } from '@management-models/PasswordSettings';
import { NewPasswordPolicy } from '@management-models/NewPasswordPolicy';

export interface DomainTestSettings {
  inherited: boolean;
  settings: {
    accountSettings?: AccountSettings;
    passwordSettings?: PasswordSettings;
  };
  passwordPolicy?: NewPasswordPolicy;
}

export const expectedMsg = (setting: DomainTestSettings) => {
  return setting.settings.accountSettings.autoLoginAfterResetPassword
    ? setting.settings.accountSettings.redirectUriAfterResetPassword
    : 'success=reset_password_completed';
};

export const getPasswordSettingsAttribute = (setting: DomainTestSettings, attrName) => {
  if (!setting.inherited) {
    return setting.settings.passwordSettings[attrName];
  }

  if (setting.passwordPolicy) {
    return setting.passwordPolicy[attrName];
  }

  return null;
};
