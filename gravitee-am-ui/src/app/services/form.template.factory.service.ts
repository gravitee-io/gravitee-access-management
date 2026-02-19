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
import { Injectable } from '@angular/core';

@Injectable()
export class FormTemplateFactoryService {
  private static formTemplates = {
    login: {
      name: 'Login',
      description: 'Login page to authenticate users',
      template: 'LOGIN',
      icon: 'account_box',
    },
    identifier_first_login: {
      name: 'Identifier-first login',
      description: 'Identifier-first login page to authenticate users',
      template: 'IDENTIFIER_FIRST_LOGIN',
      icon: 'account_box',
    },
    webauthn_login: {
      name: 'WebAuthn Login',
      description: 'Passwordless page to authenticate users',
      template: 'WEBAUTHN_LOGIN',
      icon: 'fingerprint',
    },
    webauthn_register: {
      name: 'WebAuthn Register',
      description: 'Passwordless page to register authenticators (devices)',
      template: 'WEBAUTHN_REGISTER',
      icon: 'fingerprint',
    },
    'webauthn register success': {
      name: 'Webauthn Register Success',
      description: 'Passwordless page to finalize the registration (naming)',
      template: 'WEBAUTHN_REGISTER_SUCCESS',
      icon: 'fingerprint',
    },
    registration: {
      name: 'Registration',
      description: 'Registration page to create an account',
      template: 'REGISTRATION',
      icon: 'person_add',
    },
    registration_confirmation: {
      name: 'Registration confirmation',
      description: 'Register page to confirm user account',
      template: 'REGISTRATION_CONFIRMATION',
      icon: 'how_to_reg',
    },
    registration_verify: {
      name: 'Account registered verification',
      description: 'Page confirming the verification of the account',
      template: 'REGISTRATION_VERIFY',
      icon: 'how_to_reg',
    },
    forgot_password: {
      name: 'Forgot password',
      description: 'Forgot password to recover account',
      template: 'FORGOT_PASSWORD',
      icon: 'lock',
    },
    reset_password: {
      name: 'Reset password',
      description: 'Reset password page to make a new password',
      template: 'RESET_PASSWORD',
      icon: 'lock_open',
    },
    oauth2_user_consent: {
      name: 'User consent',
      description: 'User consent to acknowledge and accept data access',
      template: 'OAUTH2_USER_CONSENT',
      icon: 'playlist_add_check',
    },
    mfa_enroll: {
      name: 'MFA Enroll',
      description: 'Multi-factor authentication settings page',
      template: 'MFA_ENROLL',
      icon: 'rotate_right',
    },
    mfa_challenge: {
      name: 'MFA Challenge',
      description: 'Multi-factor authentication verify page',
      template: 'MFA_CHALLENGE',
      icon: 'check_circle_outline',
    },
    mfa_challenge_alternatives: {
      name: 'MFA Challenge alternatives',
      description: 'Multi-factor authentication alternatives page',
      template: 'MFA_CHALLENGE_ALTERNATIVES',
      icon: 'swap_horiz',
    },
    mfa_recovery_code: {
      name: 'Recovery Codes',
      description: 'Multi-factor authentication recovery code page',
      template: 'MFA_RECOVERY_CODE',
      icon: 'autorenew',
    },
    cba_login: {
      name: 'CBA Login',
      description: 'Certificate based authentication login page',
      template: 'CBA_LOGIN',
      icon: 'fingerprint',
    },
    magic_link_login: {
      name: 'Magic Link Login',
      description: 'Magic Link authentication login page',
      template: 'MAGIC_LINK_LOGIN',
      icon: 'fingerprint',
    },
    error: {
      name: 'Error',
      description: 'Error page to display a message describing the problem',
      template: 'ERROR',
      icon: 'error_outline',
    },
  };

  findAll(): any[] {
    return Object.values(FormTemplateFactoryService.formTemplates).map((template) => {
      return { ...template };
    });
  }

  findBy(predicate: (templateName: any) => boolean): any[] {
    return this.findAll().filter(predicate);
  }
}
