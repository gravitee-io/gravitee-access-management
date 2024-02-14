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
enum MfaSelectIcon {
  OTP = 'mobile_friendly',
  SMS = 'sms',
  EMAIL = 'email',
  CALL = 'call',
  HTTP = 'http',
  RECOVERY_CODE = 'autorenew',
  FIDO2 = 'fingerprint',
}

enum MfaType {
  OTP = 'TOTP',
  SMS = 'SMS',
  EMAIL = 'EMAIL',
  CALL = 'CALL',
  HTTP = 'HTTP',
  RECOVERY_CODE = 'Recovery Code',
  FIDO2 = 'FIDO2',
}

export function getFactorTypeIcon(type: string): string {
  const factorType = type.toUpperCase();
  if (MfaSelectIcon[factorType]) {
    return MfaSelectIcon[factorType];
  } else {
    return 'donut_large';
  }
}

export function getDisplayFactorType(type: string): string {
  const factorType = type.toUpperCase();
  if (MfaType[factorType]) {
    return MfaType[factorType];
  } else {
    return 'Custom';
  }
}
