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

/**
 * NOTE: MFA rate limiting must be enabled on the gateway for these tests to exercise the rate limiter.
 * It is disabled by default (`mfa_rate.enabled=false`); enable it via `gravitee.yml` or the corresponding
 * `GRAVITEE_MFA_RATE_*` environment variables before starting the AM Gateway.
 */
import { afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  safeDeleteDomain,
  patchDomain,
  startDomain,
  waitForDomainStart,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import {
  createApplication,
  updateApplication,
  updateApplicationFlows,
  getApplicationFlows,
} from '@management-commands/application-management-commands';
import { createFactor } from '@management-commands/factor-management-commands';
import { createResource } from '@management-commands/resource-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { setup } from '../../test-fixture';
import { extractXsrfToken, getWellKnownOpenIdConfiguration, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { clearEmails, getLastEmail, hasEmail } from '@utils-commands/email-commands';
import { uniqueName } from '@utils-commands/misc';
import { FlowEntityTypeEnum } from '../../../api/management/models';
import { withRetry } from '@utils-commands/retry';

setup(200000);

let domain;
let application;
let emailFactor;
let smtpResource;
let accessToken;
let defaultIdp;
let clientId;
let scimClient;
let scimAccessToken;
let scimEndpoint;

const createSMTPResource = async (domain, accessToken) => {
  const smtp = await createResource(domain.id, accessToken, {
    type: 'smtp-am-resource',
    configuration: `{"host":"${process.env.INTERNAL_FAKE_SMTP_HOST}","port":${process.env.INTERNAL_FAKE_SMTP_PORT},"from":"admin@test.com","protocol":"smtp","authentication":false,"startTls":false}`,
    name: 'FakeSmtp',
  });

  expect(smtp.id).toBeDefined();
  return smtp;
};

const createEmailFactor = async (smtpResource, domain, accessToken) => {
  const factor = await createFactor(domain.id, accessToken, {
    type: 'email-am-factor',
    factorType: 'EMAIL',
    configuration: `{"graviteeResource":"${smtpResource.id}","returnDigits":6}`,
    name: 'Email',
  });

  expect(factor).toBeDefined();
  return factor;
};

/**
 * Helper function to set up a pre-registered user via SCIM and extract the registration confirmation link.
 * Uses SCIM endpoint to create user, which simulates real-world provisioning scenarios.
 */
const setupPreRegisteredUserAndGetConfirmationLink = async (domain, scimAccessToken, scimEndpoint, clientId) => {
  const username = uniqueName('test-user', true);
  const email = `test-${Date.now()}@example.com`;

  const scimUserRequest = {
    schemas: ['urn:ietf:params:scim:schemas:extension:custom:2.0:User', 'urn:ietf:params:scim:schemas:core:2.0:User'],
    userName: username,
    name: {
      familyName: 'User',
      givenName: 'Test',
    },
    emails: [
      {
        value: email,
        type: 'work',
        primary: true,
      },
    ],
    active: true,
    'urn:ietf:params:scim:schemas:extension:custom:2.0:User': {
      preRegistration: true,
    },
  };

  const scimResponse = await performPost(scimEndpoint, '/Users', JSON.stringify(scimUserRequest), {
    'Content-type': 'application/json',
    Authorization: `Bearer ${scimAccessToken}`,
  });

  expect(scimResponse.status).toBe(201);
  const createdUser = scimResponse.body;
  expect(createdUser).toBeDefined();
  expect(createdUser.active).toBeFalsy(); // Pre-registered users are not active

  // Wait for registration email
  await withRetry(
    async () => {
      const emailExists = await hasEmail();
      if (!emailExists) {
        throw new Error('Email not received yet');
      }
      return emailExists;
    },
    30,
    500,
  );

  const confirmationLink = (await getLastEmail()).extractLink();
  await clearEmails();

  const resetPwdToken = new URL(confirmationLink).searchParams.get('token');
  const baseUrlConfirmRegister = confirmationLink.substring(0, confirmationLink.indexOf('?'));

  // Add client_id to registration link and extract XSRF token
  const url = new URL(confirmationLink);
  url.searchParams.set('client_id', clientId);
  const registrationLinkWithClientId = url.toString();
  const { headers } = await extractXsrfToken(baseUrlConfirmRegister, `?token=${resetPwdToken}&client_id=${clientId}`);

  // Load registration confirmation page (enrolls MFA via flow policy)
  const registrationPage = await performGet(registrationLinkWithClientId, '', {
    Cookie: headers['set-cookie'],
  });
  expect(registrationPage.status).toBe(200);

  return {
    resetPwdToken,
    headers,
  };
};

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  domain = await createDomain(accessToken, uniqueName('mfa-rate-limit-registration', true), 'MFA rate limit registration test');
  expect(domain).toBeDefined();
  expect(domain.id).toBeDefined();

  // Enable SCIM on the domain (required for SCIM user creation)
  await patchDomain(domain.id, accessToken, {
    scim: {
      enabled: true,
      idpSelectionEnabled: false,
    },
  });

  smtpResource = await createSMTPResource(domain, accessToken);
  emailFactor = await createEmailFactor(smtpResource, domain, accessToken);

  const idpSet = await getAllIdps(domain.id, accessToken);
  defaultIdp = idpSet.values().next().value.id;

  application = await createApplication(domain.id, accessToken, {
    name: 'mfa-registration-app',
    type: 'WEB',
    clientId: 'mfa-registration-client',
    clientSecret: 'mfa-registration-secret',
    redirectUris: ['https://callback'],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['https://callback'],
            grantTypes: ['authorization_code'],
          },
          mfa: {
            factor: {
              defaultFactorId: emailFactor.id,
              applicationFactors: [{ id: emailFactor.id }],
            },
            challenge: {
              active: true,
              challengeRule: '',
              type: 'required',
            },
            enroll: {
              active: true,
              enrollmentSkipActive: false,
              forceEnrollment: true,
              type: 'required',
            },
          },
        },
        identityProviders: [{ identity: defaultIdp, priority: -1 }],
      },
      app.id,
    ).then((updatedApp) => {
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );
  expect(application).toBeDefined();
  clientId = application.settings.oauth.clientId;

  // Create SCIM client application for SCIM user creation
  const scimApplicationDefinition = {
    name: 'SCIM App',
    type: 'SERVICE',
    settings: {
      oauth: {
        grantTypes: ['client_credentials'],
        scopeSettings: [
          {
            scope: 'scim',
            defaultScope: true,
          },
        ],
      },
    },
  };

  scimClient = await createApplication(domain.id, accessToken, scimApplicationDefinition);
  expect(scimClient).toBeDefined();
  // Update app as CREATE doesn't always handle scope settings correctly
  await updateApplication(domain.id, accessToken, scimApplicationDefinition, scimClient.id);

  const domainStarted = await startDomain(domain.id, accessToken);
  expect(domainStarted).toBeDefined();
  domain = domainStarted;

  await waitForDomainStart(domain);
  await waitForDomainSync(domain.id);

  // Get existing flows and add RegistrationConfirmation flow
  // Note: Flows must be created after domain is started
  const flows = await getApplicationFlows(domain.id, accessToken, application.id);
  flows.push({
    name: 'Registration Confirmation Flow',
    type: FlowEntityTypeEnum.RegistrationConfirmation,
    condition: '',
    enabled: true,
    pre: [
      {
        name: 'Enroll MFA',
        policy: 'policy-am-enroll-mfa',
        description: '',
        enabled: true,
        configuration: JSON.stringify({
          factorId: emailFactor.id,
          value: "{#context.attributes['user'].email}",
          primary: true,
        }),
        condition: '',
      },
    ],
    post: [],
  });

  await updateApplicationFlows(domain.id, accessToken, application.id, flows);
  await waitForDomainSync(domain.id);

  // Get SCIM access token
  const openIdConfiguration = await getWellKnownOpenIdConfiguration(domain.hrid);
  const tokenResponse = await performPost(openIdConfiguration.body.token_endpoint, '', 'grant_type=client_credentials', {
    'Content-type': 'application/x-www-form-urlencoded',
    Authorization: 'Basic ' + applicationBase64Token(scimClient),
  });
  scimAccessToken = tokenResponse.body.access_token;
  expect(scimAccessToken).toBeDefined();
  scimEndpoint = `${process.env.AM_GATEWAY_URL}/${domain.hrid}/scim`;
});

describe('MFA Rate Limit during Registration Confirmation', () => {
  /**
   * Test Purpose: Verify MFA rate limiting works correctly during registration confirmation flow
   *
   * This test verifies:
   * 1. Rate limiter correctly handles registration confirmation scenarios (where user client may be null)
   * 2. Rate limiter enforcement: Multiple requests are properly rate limited
   * 3. No database errors or unexpected failures occur during rate limiting
   *
   * Assumptions:
   * - MFA rate limit is enabled (configured via gateway settings, not by this test)
   * - Default CI/CD configuration is used (e.g. limit 2 in 1 minute)
   *
   * Expected behaviour:
   * - First 2 requests to /mfa/challenge succeed with HTTP 200
   * - 3rd request is rate limited and returns HTTP 302 redirect
   * - Redirect URL contains request_limit_error=mfa_request_limit_exceed and the original parameters
   * - No database errors or unexpected failures are present in any response
   */
  it('should handle MFA challenge requests and enforce rate limit during registration confirmation', async () => {
    const { resetPwdToken, headers } = await setupPreRegisteredUserAndGetConfirmationLink(domain, scimAccessToken, scimEndpoint, clientId);

    const challengeUrl = `${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge?client_id=${clientId}&token=${resetPwdToken}`;

    // First two requests should succeed with HTTP 200 (rate limit not reached yet)
    for (let i = 0; i < 2; i++) {
      const response = await performGet(challengeUrl, '', {
        Cookie: headers['set-cookie'],
      });

      expect(response.status).toBe(200);
      expect(response.text).toBeDefined();

      // Verify the response is the MFA challenge page (not an error page)
      const pageContent = response.text || '';
      expect(pageContent).toContain('Authenticate your account');
      expect(pageContent).toContain('X-XSRF-TOKEN');
      expect(pageContent).toContain('name="code"');
    }

    // Third request should be rate limited and return HTTP 302 redirect
    const rateLimitedResponse = await performGet(challengeUrl, '', {
      Cookie: headers['set-cookie'],
    });

    expect(rateLimitedResponse.status).toBe(302);

    const redirectLocation = rateLimitedResponse.headers['location'] || '';

    // Verify rate limit redirect contains expected parameters
    expect(redirectLocation).toContain('/mfa/challenge');
    expect(redirectLocation).toContain(`client_id=${clientId}`);
    expect(redirectLocation).toContain(`token=${resetPwdToken}`);
    expect(redirectLocation).toContain('request_limit_error=mfa_request_limit_exceed');
  });
});

afterAll(async () => {
  await safeDeleteDomain(domain?.id, accessToken);
});
