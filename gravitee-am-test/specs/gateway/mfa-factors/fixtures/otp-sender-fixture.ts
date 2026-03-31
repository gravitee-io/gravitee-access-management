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
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import {
  createDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
  DomainOidcConfig,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { createResource } from '@management-commands/resource-management-commands';
import { createFactor } from '@management-commands/factor-management-commands';
import { Fixture } from '../../../test-fixture';
import { createMfaApplication } from './mfa-flow-helpers';

export interface OtpSenderFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  application: Application;
  otpSenderFactor: any;
}

/** Sets up OTP sender factor with SMTP resource (fakeSMTP) and MFA-enabled app. */
export const setupOtpSenderFixture = async (): Promise<OtpSenderFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    domain = await createDomain(accessToken, uniqueName('otp-sender', true), 'OTP sender factor test domain');

    const smtpResource = await createResource(domain.id, accessToken, {
      type: 'smtp-am-resource',
      configuration: JSON.stringify({
        host: process.env.INTERNAL_FAKE_SMTP_HOST || 'smtp',
        port: parseInt(process.env.INTERNAL_FAKE_SMTP_PORT || '5025'),
        from: 'admin@test.com',
        protocol: 'smtp',
        authentication: false,
        startTls: false,
      }),
      name: uniqueName('otp-sender-smtp', true),
    });

    // No waitForSyncAfter — domain is not on the gateway until startDomain
    const otpSenderFactor = await createFactor(domain.id, accessToken, {
      type: 'otp-sender-am-factor',
      factorType: 'TOTP',
      name: uniqueName('otp-sender-factor', true),
      configuration: JSON.stringify({
        issuer: 'Gravitee.io',
        algorithm: 'HmacSHA1',
        timeStep: 30,
        returnDigits: 6,
        senders: [
          {
            resource: smtpResource.id,
            to: '{#context.attributes[\'user\'][\'email\']}',
          },
        ],
      }),
    });

    const application = await createMfaApplication(domain.id, accessToken, otpSenderFactor.id, 'otp-sender');

    // Resources/factors must exist before startDomain (create-before-start pattern)
    const startedDomain = await startDomain(domain.id, accessToken);
    const domainWithOidc = await waitForDomainStart(startedDomain);

    return {
      domain: domainWithOidc.domain,
      accessToken,
      oidc: domainWithOidc.oidcConfig,
      application,
      otpSenderFactor,
      cleanUp: async () => {
        if (domain?.id && accessToken) {
          await safeDeleteDomain(domain.id, accessToken);
        }
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (e) {
        console.error('Cleanup failed:', e);
      }
    }
    throw error;
  }
};
