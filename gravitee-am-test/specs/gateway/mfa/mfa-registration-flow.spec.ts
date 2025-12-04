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
 * E2E test for user registration flow with MFA enrollment and session management
 *
 * This test verifies the complete registration flow with MFA (enrollment, challenge, OTP verification)
 * and ensures that when autoLogin is disabled, session cleanup works correctly to allow
 * subsequent user registrations to proceed without interference from previous registration sessions.
 */
import fetch from 'cross-fetch';
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  safeDeleteDomain,
  patchDomain,
  startDomain,
  waitForDomainStart,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { createApplication, updateApplication, updateApplicationFlows, getApplicationFlows } from '@management-commands/application-management-commands';
import { createFactor } from '@management-commands/factor-management-commands';
import { createResource } from '@management-commands/resource-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { extractXsrfToken, getWellKnownOpenIdConfiguration, performGet, performPost, performFormPost } from '@gateway-commands/oauth-oidc-commands';
import { extractDomValue } from './fixture/mfa-extract-fixture';
import { applicationBase64Token } from '@gateway-commands/utils';
import { clearEmails, getLastEmail, hasEmail } from '@utils-commands/email-commands';
import { uniqueName } from '@utils-commands/misc';
import { FlowEntityTypeEnum } from '../../../api/management/models';
import { withRetry } from '@utils-commands/retry';

const cheerio = require('cheerio');

globalThis.fetch = fetch;

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

jest.setTimeout(200000);

const TEST_PASSWORD = 'TestP@ssw0rd123';

const createSMTPResource = async (domain, accessToken) => {
  const smtpHost = process.env.GRAVITEE_EMAIL_HOST || 'localhost';
  const smtpPort = process.env.GRAVITEE_EMAIL_PORT || '5025';

  const smtp = await createResource(domain.id, accessToken, {
    type: 'smtp-am-resource',
    configuration: `{"host":"${smtpHost}","port":${smtpPort},"from":"admin@test.com","protocol":"smtp","authentication":false,"startTls":false}`,
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
 * Creates a pre-registered user via SCIM and waits for the registration email.
 * Returns the confirmation link from the email.
 */
const createPreRegisteredUser = async (scimAccessToken, scimEndpoint) => {
  const username = uniqueName('test-user', true);
  const email = `test-${Date.now()}@example.com`;
  
  const scimUserRequest = {
    schemas: [
      'urn:ietf:params:scim:schemas:extension:custom:2.0:User',
      'urn:ietf:params:scim:schemas:core:2.0:User'
    ],
    userName: username,
    name: {
      familyName: 'User',
      givenName: 'Test',
    },
    emails: [
      {
        value: email,
        type: 'work',
        primary: true
      }
    ],
    active: true,
    'urn:ietf:params:scim:schemas:extension:custom:2.0:User': {
      preRegistration: true
    }
  };

  const scimResponse = await performPost(scimEndpoint, '/Users', JSON.stringify(scimUserRequest), {
    'Content-type': 'application/json',
    Authorization: `Bearer ${scimAccessToken}`,
  });
  
  expect(scimResponse.status).toBe(201);
  const createdUser = scimResponse.body;
  expect(createdUser).toBeDefined();
  expect(createdUser.active).toBeFalsy();

  await withRetry(async () => {
    const emailExists = await hasEmail();
    if (!emailExists) {
      throw new Error('Email not received yet');
    }
    return emailExists;
  }, 30, 500);

  const confirmationLink = (await getLastEmail()).extractLink();
  await clearEmails();

  return confirmationLink;
};

/**
 * Sets up a pre-registered user and returns necessary artifacts for the registration flow.
 * Shared by both the \"complete registration\" helper and the main test.
 */
const setupPreRegisteredUser = async (scimAccessToken, scimEndpoint, clientId) => {
  const confirmationLink = await createPreRegisteredUser(scimAccessToken, scimEndpoint);

  const resetPwdToken = new URL(confirmationLink).searchParams.get('token');
  const baseUrlConfirmRegister = confirmationLink.substring(0, confirmationLink.indexOf('?'));

  // Add client_id to registration link and extract XSRF token
  const url = new URL(confirmationLink);
  url.searchParams.set('client_id', clientId);
  const registrationLinkWithClientId = url.toString();
  const { headers } = await extractXsrfToken(baseUrlConfirmRegister, `?token=${resetPwdToken}&client_id=${clientId}`);

  return { registrationLinkWithClientId, resetPwdToken, headers };
};

/**
 * Completes the full registration flow: MFA enrollment (via flow policy), MFA challenge,
 * OTP verification, and password submission. When autoLogin is disabled, this flow
 * triggers session destruction to ensure subsequent user registrations are not affected.
 */
const setupAndCompleteRegistration = async (domain, scimAccessToken, scimEndpoint, clientId) => {
  const { registrationLinkWithClientId, resetPwdToken, headers } = await setupPreRegisteredUser(
    scimAccessToken,
    scimEndpoint,
    clientId,
  );

  // Load registration confirmation page (enrolls MFA via flow policy)
  const registrationPage = await performGet(registrationLinkWithClientId, '', {
    Cookie: headers['set-cookie'],
  });
  expect(registrationPage.status).toBe(200);

  // Navigate to MFA challenge page (MFA challenge is triggered via flow policy)
  const challengeUrl = `${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge?client_id=${clientId}&token=${resetPwdToken}`;
  const challengePage = await performGet(challengeUrl, '', {
    Cookie: headers['set-cookie'],
  });
  expect(challengePage.status).toBe(200);
  expect(challengePage.text).toContain('Authenticate your account'); // MFA challenge page

  const dom = cheerio.load(challengePage.text);
  const xsrfToken = dom('[name=X-XSRF-TOKEN]').val();
  const action = dom('form').attr('action');
  const factorId = extractDomValue(challengePage, '[name=factorId]');
  
  expect(xsrfToken).toBeDefined();
  expect(action).toBeDefined();
  
  // Get OTP from email
  await withRetry(async () => {
    const emailExists = await hasEmail();
    if (!emailExists) {
      throw new Error('OTP email not received yet');
    }
    return emailExists;
  }, 30, 500);

  const otpEmail = await getLastEmail();
  const otpRegex = /\b\d{6}\b/;
  const otpMatch = otpRegex.exec(otpEmail.contents[0].data);
  expect(otpMatch).toBeDefined();
  const otpCode = otpMatch[0];
  await clearEmails();

  // Submit OTP code
  const challengeSubmitResponse = await performFormPost(
    action,
    '',
    {
      'X-XSRF-TOKEN': xsrfToken,
      code: otpCode,
      factorId: factorId,
    },
    {
      Cookie: challengePage.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  );

  expect(challengeSubmitResponse.status).toBe(302);
  
  const passwordPageResponse = await performGet(registrationLinkWithClientId, '', {
    Cookie: challengeSubmitResponse.headers['set-cookie'],
  });
  
  expect(passwordPageResponse.status).toBe(200);
  expect(passwordPageResponse.text).toContain('password');

  const passwordDom = cheerio.load(passwordPageResponse.text);
  const passwordXsrfToken = passwordDom('[name=X-XSRF-TOKEN]').val();
  const passwordAction = passwordDom('form').attr('action');
  
  expect(passwordXsrfToken).toBeDefined();
  expect(passwordAction).toBeDefined();
  
  const passwordSubmitResponse = await performFormPost(
    passwordAction,
    '',
    {
      'X-XSRF-TOKEN': passwordXsrfToken,
      password: TEST_PASSWORD,
      password_confirm: TEST_PASSWORD,
    },
    {
      Cookie: passwordPageResponse.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  );

  expect(passwordSubmitResponse.status).toBe(302);
};

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  domain = await createDomain(accessToken, uniqueName('mfa-registration-flow', true), 'User registration with MFA enrollment test');
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
    name: 'mfa-registration-flow-app',
    type: 'WEB',
    clientId: 'mfa-registration-flow-client',
    clientSecret: 'mfa-registration-flow-secret',
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
          // Disable autoLogin to verify session is destroyed after registration
          account: {
            inherited: false,
            autoLoginAfterRegistration: false,
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
  await updateApplication(domain.id, accessToken, scimApplicationDefinition, scimClient.id);

  const domainStarted = await startDomain(domain.id, accessToken);
  expect(domainStarted).toBeDefined();
  domain = domainStarted;

  await waitForDomainStart(domain);
  await waitForDomainSync(domain.id, accessToken);

  // Get existing flows and add RegistrationConfirmation flow
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
  await waitForDomainSync(domain.id, accessToken);

  const openIdConfiguration = await getWellKnownOpenIdConfiguration(domain.hrid);
  const tokenResponse = await performPost(openIdConfiguration.body.token_endpoint, '', 'grant_type=client_credentials', {
    'Content-type': 'application/x-www-form-urlencoded',
    Authorization: 'Basic ' + applicationBase64Token(scimClient),
  });
  scimAccessToken = tokenResponse.body.access_token;
  expect(scimAccessToken).toBeDefined();
  scimEndpoint = `${process.env.AM_GATEWAY_URL}/${domain.hrid}/scim`;
});

describe('User registration with MFA enrollment and session management', () => {
  it('should complete registration flow with MFA and ensure session cleanup allows subsequent registrations', async () => {
    await setupAndCompleteRegistration(domain, scimAccessToken, scimEndpoint, clientId);

    const { registrationLinkWithClientId: newUserRegistrationLink, resetPwdToken: newUserResetPwdToken, headers: newUserHeaders } = await setupPreRegisteredUser(
      scimAccessToken,
      scimEndpoint,
      clientId,
    );

    const newUserRegistrationPage = await performGet(newUserRegistrationLink, '', {
      Cookie: newUserHeaders['set-cookie'],
    });
    expect(newUserRegistrationPage.status).toBe(200);

    const newUserChallengeUrl = `${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge?client_id=${clientId}&token=${newUserResetPwdToken}`;
    const newUserChallengePage = await performGet(newUserChallengeUrl, '', {
      Cookie: newUserHeaders['set-cookie'],
    });

    expect(newUserChallengePage.status).toBe(200);
    
    const pageContent = newUserChallengePage.text || '';
    expect(pageContent).toContain('Authenticate your account');
    expect(pageContent).toContain('X-XSRF-TOKEN');
    expect(pageContent).toContain('name="code"');
    expect(pageContent).not.toContain('Sign-up confirmation');
  });
});

afterAll(async () => {
  await safeDeleteDomain(domain?.id, accessToken);
});

