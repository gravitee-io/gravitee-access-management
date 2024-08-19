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
import * as faker from 'faker';
import fetch from 'cross-fetch';
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  deleteDomain,
  patchDomain,
  startDomain,
  waitFor,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import {
  extractXsrfTokenAndActionResponse,
  getWellKnownOpenIdConfiguration,
  performFormPost,
  performGet,
  performPost,
} from '@gateway-commands/oauth-oidc-commands';
import cheerio from 'cheerio';
import { createUser } from '@management-commands/user-management-commands';
import { clearEmails, getLastEmail } from '@utils-commands/email-commands';
import { applicationBase64Token } from '@gateway-commands/utils';

global.fetch = fetch;

let domain;
let accessToken;
let application;
let openIdConfiguration;
let user;
let confirmationLink;
let domainIds = new Set();
let clientId;
let userSessionToken;
const resetPasswordFailed = 'error=reset_password_failed';
const invalidPasswordValue = 'invalid_password_value';
const userProps = {
  firstName: 'firstName',
  lastName: 'lastName',
  email: 'test@mail.com',
  username: `test123`,
  password: 'SomeP@ssw0rd01',
};

jest.setTimeout(200000);
const settings = [
  {
    inherited: false,
    app: {
      accountSettings: {
        inherited: false,
        autoLoginAfterResetPassword: true,
        redirectUriAfterResetPassword: 'http://localhost:4000',
      },
      passwordSettings: {
        inherited: false,
        minLength: 5,
        maxLength: 24,
        excludePasswordsInDictionary: true,
        excludeUserProfileInfoInPassword: true,
      },
    },
  },
  {
    inherited: true,
    domain: {
      accountSettings: {
        deletePasswordlessDevicesAfterResetPassword: true,
        autoLoginAfterResetPassword: true,
        redirectUriAfterResetPassword: 'http://localhost:4000',
      },
      passwordSettings: {
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
    },
  },
  {
    inherited: true,
    domain: {
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
      passwordSettings: {
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
    },
  },
];

const selectSetting = (setting) => {
  return setting.inherited ? setting.domain : setting.app;
};

const expectedMsg = (setting) => {
  return selectSetting(setting).accountSettings.autoLoginAfterResetPassword
    ? selectSetting(setting).accountSettings.redirectUriAfterResetPassword
    : 'success=reset_password_completed';
};

settings.forEach((setting) => {
  const selectedSetting = selectSetting(setting);
  describe('Gateway reset password', () => {
    beforeAll(async () => {
      const adminTokenResponse = await requestAdminAccessToken();
      accessToken = adminTokenResponse.body.access_token;
      domain = await createDomain(accessToken, faker.company.companyName(0), 'test Gateway reset password with password history');
      domainIds.add(domain.id);
      domain = await patchDomain(domain.id, accessToken, {
        master: true,
        oidc: {
          clientRegistrationSettings: {
            allowLocalhostRedirectUri: true,
            allowHttpSchemeRedirectUri: true,
            allowWildCardRedirectUri: true,
          },
        },
        loginSettings: {
          forgotPasswordEnabled: true,
        },
        accountSettings: setting.inherited ? selectedSetting.accountSettings : {},
        passwordSettings: setting.inherited ? selectedSetting.passwordSettings : {},
      });

      application = await createApplication(domain.id, accessToken, {
        name: faker.company.bsBuzz(),
        type: 'WEB',
        clientId: faker.company.bsAdjective(),
      }).then((app) =>
        updateApplication(
          domain.id,
          accessToken,
          {
            settings: {
              oauth: {
                redirectUris: ['http://localhost:4000'],
                grantTypes: ['implicit', 'authorization_code', 'password', 'refresh_token'],
                scopeSettings: [
                  {
                    scope: 'openid',
                    defaultScope: true,
                  },
                ],
              },
              account: setting.inherited ? {} : selectedSetting.accountSettings,
              passwordSettings: setting.inherited ? {} : selectedSetting.passwordSettings,
            },
            identityProviders: [{ identity: `default-idp-${domain.id}`, priority: 0 }],
          },
          app.id,
        ).then((updatedApp) => {
          // restore the clientSecret coming from the create order
          updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
          return updatedApp;
        }),
      );
      clientId = application.settings.oauth.clientId;

      domain = await startDomain(domain.id, accessToken);
      await waitForDomainSync();
      const result = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
      openIdConfiguration = result.body;

      user = await createUser(domain.id, accessToken, userProps);
      await waitFor(1000);
      if (selectedSetting.accountSettings.resetPasswordConfirmIdentity) {
        await createUser(domain.id, accessToken, {
          firstName: 'second',
          lastName: 'user',
          email: 'test@mail.com',
          username: `anotherUser`,
          password: 'r@Nd0mP@55w0rd!',
        });
        await waitFor(1000);
      }
      const response = await performPost(
        openIdConfiguration.token_endpoint,
        '',
        'grant_type=password&username=test123&password=SomeP@ssw0rd01&scope=openid',
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + applicationBase64Token(application),
        },
      ).expect(200);
      userSessionToken = response.body.access_token;
    });

    const passwordHistoryTests = [
      {
        password: 'SomeP@ssw0rd01',
        expectedMsg: resetPasswordFailed,
      },
      {
        password: 'SomeP@ssw0rd02',
        expectedMsg: expectedMsg(setting),
      },
      {
        password: 'SomeP@ssw0rd03',
        expectedMsg: expectedMsg(setting),
      },
      {
        password: 'SomeP@ssw0rd04',
        expectedMsg: expectedMsg(setting),
      },
      {
        password: 'SomeP@ssw0rd01',
        expectedMsg: expectedMsg(setting),
      },
    ];

    if (selectedSetting.passwordSettings.passwordHistoryEnabled) {
      describe(`when password history is enabled for ${selectedSetting.passwordSettings.oldPasswords} passwords`, () => {
        passwordHistoryTests.forEach(({ password, expectedMsg }) => {
          describe(`when resetting password with ${password}`, () => {
            it('should redirect to forgot password form', async () => {
              await forgotPassword(selectedSetting);
            });

            it('should receive an email with a link to the reset password form', async () => {
              await retrieveEmailLinkForReset();
            });

            it(`${password} should return ${expectedMsg}`, async () => {
              await resetPassword(password, expectedMsg, selectedSetting);
            });

            afterAll(async () => {
              await waitFor(1000);
            });
          });
        });
      });
    }

    describe('when a Password Policy has been configured', () => {
      beforeAll(async () => {
        await forgotPassword(selectedSetting);
        await retrieveEmailLinkForReset();
      });

      if (selectedSetting.passwordSettings.minLength) {
        describe(`when a password is shorter than the minimum length of ${selectedSetting.passwordSettings.minLength}`, () => {
          const minLength = 'SomeP@ssw0rd99'.substring(0, selectedSetting.passwordSettings.minLength - 1);
          it(`reset password should fail with ${invalidPasswordValue}`, async () => {
            await resetPassword(minLength, invalidPasswordValue, selectedSetting);
          });
        });
      }

      if (selectedSetting.passwordSettings.maxLength) {
        describe(`when a password is longer than the maximum length of ${selectedSetting.passwordSettings.maxLength}`, () => {
          let maxLength = 'SomeP@ssw0rd99';
          while (maxLength.length <= selectedSetting.passwordSettings.maxLength) {
            maxLength += maxLength;
          }
          it(`reset password should fail with ${invalidPasswordValue}`, async () => {
            await resetPassword(maxLength, invalidPasswordValue, selectedSetting);
          });
        });
      }

      if (selectedSetting.passwordSettings.includeNumbers) {
        describe("when 'includeNumbers' is enabled and a password does not contain numbers", () => {
          it(`reset password should fail with ${invalidPasswordValue}`, async () => {
            await resetPassword('SomeP@ssword', invalidPasswordValue, selectedSetting);
          });
        });
      }

      if (selectedSetting.passwordSettings.includeSpecialCharacters) {
        describe("when 'includeSpecialCharacters' is enabled and a password does not contain special characters", () => {
          if (selectedSetting.passwordSettings.includeSpecialCharacters) {
            it(`reset password should fail with ${invalidPasswordValue}`, async () => {
              await resetPassword('SomePassw0rd99', invalidPasswordValue, selectedSetting);
            });
          }
        });
      }

      if (selectedSetting.passwordSettings.lettersInMixedCase) {
        describe("when 'lettersInMixedCase' is enabled and a password is not mixed case", () => {
          it(`reset password should fail with ${invalidPasswordValue}`, async () => {
            await resetPassword('somepassw0rd99', invalidPasswordValue, selectedSetting);
          });
        });
      }

      if (selectedSetting.passwordSettings.maxConsecutiveLetters) {
        describe(`when password contains sequence exceeding ${selectedSetting.passwordSettings.maxConsecutiveLetters} consecutive letters`, () => {
          it(`reset password should fail with ${invalidPasswordValue}`, async () => {
            let letters = '';
            for (let i = 0; i <= selectedSetting.passwordSettings.maxConsecutiveLetters; i++) {
              letters += 'a';
            }
            let maxConsecutiveLetters = 'SomeP@ssw0rd99' + letters;
            await resetPassword(maxConsecutiveLetters, invalidPasswordValue, selectedSetting);
          });
        });
      }

      if (selectedSetting.passwordSettings.excludePasswordsInDictionary) {
        ['password', 'passw0rd', 'pass123', 'passwd', 'gravity'].forEach((password) => {
          describe(`when 'excludePasswordsInDictionary' is enabled and '${password}' from the dictionary is used`, () => {
            it(`reset password should fail with ${invalidPasswordValue}`, async () => {
              await resetPassword(password, invalidPasswordValue, selectedSetting);
            });
          });
        });
      }

      if (selectedSetting.passwordSettings.excludeUserProfileInfoInPassword) {
        Object.entries(userProps)
          .filter(([key]) => key !== 'password')
          .forEach(([propName, value]) => {
            describe(`when 'excludeUserProfileInfoInPassword' is enabled and a user's '${propName}' is used`, () => {
              it(`reset password should fail with ${invalidPasswordValue}`, async () => {
                await resetPassword(value, invalidPasswordValue, selectedSetting);
              });
            });
          });
      }

      afterAll(async () => {
        await waitFor(1000);
      });
    });
  });
});

afterAll(async () => {
  for await (const domainId of domainIds) {
    await deleteDomain(domainId, accessToken);
  }
});

const getCookieAndXsrfToken = async () => {
  const params = `?response_type=token&client_id=${clientId}&redirect_uri=http://localhost:4000`;
  const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);
  return extractXsrfTokenAndActionResponse(authResponse);
};

const params = () => {
  return `?response_type=token&client_id=${clientId}&redirect_uri=http://localhost:4000`;
};

const forgotPassword = async (settings) => {
  const uri = `/${domain.hrid}/forgotPassword` + params();
  if (settings.accountSettings.resetPasswordCustomForm && !settings.accountSettings.resetPasswordConfirmIdentity) {
    //Get the (custom) forgot password form
    const forgotResponse = await performGet(process.env.AM_GATEWAY_URL, uri);
    const dom = cheerio.load(forgotResponse.text);
    expect(dom('#username')).toBeDefined();
    expect(dom('#email')).toBeDefined();
  }

  const { headers, token } = await getCookieAndXsrfToken();
  //Submit forgot password form
  const postResponse = await performFormPost(
    process.env.AM_GATEWAY_URL,
    uri,
    {
      'X-XSRF-TOKEN': token,
      email: user.email,
      client_id: clientId,
    },
    {
      Cookie: headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

  if (settings.accountSettings.resetPasswordCustomForm && settings.accountSettings.resetPasswordConfirmIdentity) {
    expect(postResponse.headers['location']).toContain('forgot_password_confirm');
    const confirmResponse = await performGet(postResponse.headers['location'], '', {
      Cookie: headers['set-cookie'],
    });
    const dom = cheerio.load(confirmResponse.text);
    expect(dom('#username')).toBeDefined();
    expect(dom('#email')).toBeDefined();

    //Submit forgot password form
    await performFormPost(
      process.env.AM_GATEWAY_URL,
      uri,
      {
        'X-XSRF-TOKEN': token,
        username: user.username,
        email: user.email,
        client_id: clientId,
      },
      {
        Cookie: headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);
  }
};

const retrieveEmailLinkForReset = async () => {
  confirmationLink = (await getLastEmail()).extractLink();
  expect(confirmationLink).toBeDefined();
  await clearEmails();
};

const callResetPasswordWithEmailLink = async () => {
  const confirmationLinkResponse = await performGet(confirmationLink);
  const headers = confirmationLinkResponse.headers['set-cookie'];
  const dom = cheerio.load(confirmationLinkResponse.text);
  const action = dom('form').attr('action');
  const xsrfToken = dom('[name=X-XSRF-TOKEN]').val();
  const resetPwdToken = dom('[name=token]').val();
  return { headers, action, xsrfToken, resetPwdToken };
};

const resetPassword = async (password, expectedMessage, setting) => {
  const { headers, action, xsrfToken, resetPwdToken } = await callResetPasswordWithEmailLink();
  const resetResponse = await performFormPost(
    action,
    '',
    {
      'X-XSRF-TOKEN': xsrfToken,
      token: resetPwdToken,
      password: password,
      'confirm-password': password,
    },
    {
      Cookie: headers,
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);
  expect(resetResponse.headers['location']).toContain(expectedMessage);
  if (expectedMessage !== resetPasswordFailed && expectedMessage !== invalidPasswordValue) {
    if (setting.accountSettings.autoLoginAfterResetPassword) {
      const sessionJwt = resetResponse.headers['set-cookie'][0].split('=')[1].split(';')[0].split('.')[1];
      const decoded = JSON.parse(atob(sessionJwt));
      expect(decoded.userId).toEqual(user.id);
    }
    await waitFor(8000);
    const response = await performPost(openIdConfiguration.introspection_endpoint, '', `token=${userSessionToken}`, {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(application),
    }).expect(200);
    if (setting.accountSettings.resetPasswordInvalidateTokens) {
      expect(response.body.active).toBeFalsy();
    } else {
      expect(response.body.active).toBeTruthy();
    }
  }
};
