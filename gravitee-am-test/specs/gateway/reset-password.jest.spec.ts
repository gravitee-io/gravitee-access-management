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
import fetch from "cross-fetch";
import {afterAll, beforeAll, expect, jest} from "@jest/globals";
import {requestAdminAccessToken} from "@management-commands/token-management-commands";
import {createDomain, deleteDomain, patchDomain, startDomain} from "@management-commands/domain-management-commands";
import {createApplication, updateApplication} from "@management-commands/application-management-commands";
import {
    extractXsrfTokenAndActionResponse,
    getWellKnownOpenIdConfiguration,
    performFormPost,
    performGet
} from "@gateway-commands/oauth-oidc-commands";
import cheerio from "cheerio";
import {createUser} from "@management-commands/user-management-commands";
import {clearEmails, getLastEmail} from "@utils-commands/email-commands";

global.fetch = fetch;

let domain;
let accessToken;
let application;
let openIdConfiguration;
let user;
let confirmationLink;
const passwords = [
    "SomeP@ssw0rd",
    "SomeP@ssw0rd01",
    "SomeP@ssw0rd02",
    "SomeP@ssw0rd03",
];

jest.setTimeout(200000)

describe("Gateway reset password", () => {
    beforeAll(async () => {
        const adminTokenResponse = await requestAdminAccessToken();
        accessToken = adminTokenResponse.body.access_token;
        domain = await createDomain(accessToken, "test-reset-password", "test Gateway reset password with password history");
        domain = await patchDomain(domain.id, accessToken, {
            "master": true,
            "passwordSettings": {
                "passwordHistoryEnabled": true,
                "oldPasswords": 3
            },
            "oidc": {
                "clientRegistrationSettings": {
                    "allowLocalhostRedirectUri": true,
                    "allowHttpSchemeRedirectUri": true,
                    "allowWildCardRedirectUri": true
                }
            },
            "loginSettings": {
                "inherited": false,
                "forgotPasswordEnabled": true,
                "registerEnabled": false,
                "rememberMeEnabled": false,
                "passwordlessEnabled": false,
                "passwordlessRememberDeviceEnabled": false,
                "hideForm": false,
                "identifierFirstEnabled": false
            }
        });

        application = await createApplication(domain.id, accessToken, {
            "name": "my-client 1",
            "type": "WEB",
            "clientId": "clientId-test-1"
        }).then(app => updateApplication(domain.id, accessToken, {
            "settings": {
                "oauth": {
                    "redirectUris": ["http://localhost:4000"],
                    "grantTypes": ["implicit", "authorization_code", "password", "refresh_token"],
                    "scopeSettings": [{
                        "scope": "openid",
                        "defaultScope": true
                    }]
                }
            },
            "identityProviders": [
                {"identity": `default-idp-${domain.id}`, "priority": 0}
            ]
        }, app.id));

        domain = await startDomain(domain.id, accessToken);
        await new Promise((r) => setTimeout(r, 10000));
        const result = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);

        openIdConfiguration = result.body
        user = {
            firstName: "firstName",
            lastName: "lastName",
            email: "test@mail.com",
            username: `test123`,
            password: "SomeP@ssw0rd"
        }

        await createUser(domain.id, accessToken, user);
        await new Promise((r) => setTimeout(r, 1000));
    });

    describe(`Reset password with ${passwords[0]}`, () => {
        it("should redirect to forgot password form", async () => {
            await forgotPassword();
        });

        it("should receive an email with a link to the reset password form", async () => {
            await retrieveEmailLinkForReset();
        });

        it(`returns reset_password_failedas it is already in history`, async () => {
            await resetPassword(passwords[0], 'error=reset_password_failed');
        });

        afterAll(async () => {
            await new Promise((r) => setTimeout(r, 1000));
        })
    });

    describe(`Reset password with ${passwords[1]}`, () => {
        it("should redirect to forgot password form", async () => {
            await forgotPassword();
        });

        it("should receive an email with a link to the reset password form", async () => {
            await retrieveEmailLinkForReset();

        });

        it(`returns reset_password_completed as it is not in history`, async () => {
            await resetPassword(passwords[1], 'success=reset_password_completed');
        });

        afterAll(async () => {
            await new Promise((r) => setTimeout(r, 1000));
        })
    });

    describe(`Reset password with ${passwords[2]}`, () => {
        it("should redirect to forgot password form", async () => {
            await forgotPassword();
        });

        it("should receive an email with a link to the reset password form", async () => {
            await retrieveEmailLinkForReset();
        });

        it(`returns reset_password_completed as it is not in history`, async () => {
            await resetPassword(passwords[2], 'success=reset_password_completed');
        });

        afterAll(async () => {
            await new Promise((r) => setTimeout(r, 1000));
        })
    });

    describe(`Reset password with ${passwords[3]}`, () => {
        it("should redirect to forgot password form", async () => {
            await forgotPassword();
        });

        it("should receive an email with a link to the reset password form", async () => {
            await retrieveEmailLinkForReset();
        });

        it(`returns reset_password_completed as it is not in history`, async () => {
            await resetPassword(passwords[3], 'success=reset_password_completed');
        });

        afterAll(async () => {
            await new Promise((r) => setTimeout(r, 1000));
        })
    });

    describe(`Reset password with ${passwords[0]} again`, () => {
        it("should redirect to forgot password form", async () => {
            await forgotPassword();
        });

        it("should receive an email with a link to the reset password form", async () => {
            await retrieveEmailLinkForReset();
        });

        it(`returns reset_password_completed as it is no longer in history`, async () => {
            await resetPassword(passwords[0], 'success=reset_password_completed');
        });

        afterAll(async () => {
            await new Promise((r) => setTimeout(r, 1000));
        })
    });
});

afterAll(async () => {
    if (domain && domain.id) {
        await deleteDomain(domain.id, accessToken);
    }
});

const forgotPassword = async () => {
    const clientId = application.settings.oauth.clientId;
    const params = `?response_type=token&client_id=${clientId}&redirect_uri=http://localhost:4000`;

    // Initiate the Login Flow
    const loginResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);
    const {headers, token} = await extractXsrfTokenAndActionResponse(loginResponse);

    const uri = `/${domain.hrid}/forgotPassword` + params;
    await performGet(process.env.AM_GATEWAY_URL, uri,
        {
            'Cookie': headers['set-cookie']
        }).expect(200);

    await performFormPost(process.env.AM_GATEWAY_URL, uri, {
            "X-XSRF-TOKEN": token,
            "email": user.email,
            "client_id": clientId
        }, {
            'Cookie': headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded'
        }
    ).expect(302);
};

const retrieveEmailLinkForReset = async () => {
    await new Promise((r) => setTimeout(r, 1000))
    confirmationLink = (await getLastEmail()).extractLink();
    expect(confirmationLink).toBeDefined();
    await clearEmails();
};

const callResetPasswordWithEmailLink = async () => {
    const confirmationLinkResponse = await performGet(confirmationLink);
    const headers = confirmationLinkResponse.headers['set-cookie'];
    const dom = cheerio.load(confirmationLinkResponse.text);
    const action = dom("form").attr('action');
    const xsrfToken = dom("[name=X-XSRF-TOKEN]").val();
    const resetPwdToken = dom("[name=token]").val();
    return {headers, action, xsrfToken, resetPwdToken};
}

const resetPassword = async (password, expectedMessage) => {
    const {headers, action, xsrfToken, resetPwdToken} = await callResetPasswordWithEmailLink();
    const resetResponse = await performFormPost(action, '', {
            "X-XSRF-TOKEN": xsrfToken,
            "token": resetPwdToken,
            "password": password,
            "confirm-password": password
        }, {
            'Cookie': headers,
            'Content-type': 'application/x-www-form-urlencoded'
        }
    ).expect(302);
    expect(resetResponse.headers['location']).toContain(expectedMessage);
}




