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
import {afterAll, beforeAll, expect, jest} from "@jest/globals";
import {createDomain, deleteDomain, startDomain, waitFor} from "@management-commands/domain-management-commands";
import {requestAdminAccessToken} from "@management-commands/token-management-commands";
import {createApplication, updateApplication} from "@management-commands/application-management-commands";

import fetch from "cross-fetch";
import {buildCreateAndTestUser, deleteUser} from "@management-commands/user-management-commands";
import {createFactor} from "@management-commands/factor-management-commands";
import {createResource} from "@management-commands/resource-management-commands";
import {extractXsrfTokenAndActionResponse, getWellKnownOpenIdConfiguration, logoutUser, performFormPost, performGet, performPost} from "@gateway-commands/oauth-oidc-commands";
import {clearEmails, getLastEmail} from "@utils-commands/email-commands";
import {TOTP} from "otpauth";
import * as faker from "faker";

const cheerio = require('cheerio');

global.fetch = fetch;

let domain;
let accessToken;
let mockFactor;
let smsResource;
let smtpResource;
let emailFactor;
let openIdConfiguration;
let smsTestApp;
let recoveryCodeFactor;
let recoveryCodeTestApp;
let bruteForceTestApp;
let emailTestApp;
let totpFactor;
let totpApp;

const mfaChallengeAttemptsResetTime = 1
const validMFACode = '333333'
const sharedSecret = "K546JFR2PK5CGQLLUTFG4W46IKDFWWUE";

jest.setTimeout(200000)

beforeAll(async () => {
    const adminTokenResponse = await requestAdminAccessToken();
    accessToken = adminTokenResponse.body.access_token;
    expect(accessToken).toBeDefined();
    domain = await createDomain(accessToken, "mfa-test-domain", "test mfa");

    smsResource = await createSMSResource(validMFACode, domain, accessToken);
    mockFactor = await createMockFactor(smsResource, domain, accessToken);

    smtpResource = await createSMTPResource(domain, accessToken);
    emailFactor = await createEmailFactor(smtpResource, domain, accessToken);

    recoveryCodeFactor = await createRecoveryCodeFactor(domain, accessToken);

    domain = await startDomain(domain.id, accessToken);

    smsTestApp = await createMfaApp(domain, accessToken, [mockFactor.id]);
    bruteForceTestApp = await createBruteForceTestApp(mockFactor, domain, accessToken, mfaChallengeAttemptsResetTime);
    emailTestApp = await createMfaApp(domain, accessToken, [emailFactor.id]);
    recoveryCodeTestApp = await createMfaApp(domain, accessToken, [mockFactor.id, recoveryCodeFactor.id]);

    totpFactor = await createOtpFactor();
    totpApp = await createMfaApp(domain, accessToken, [totpFactor.id]);

    /*
    * it is intentional to call setTimeout after creating apps. Cannot avoid this timeout call.
    * At this point I haven't found a function which is similar to retry until specific http code is returned.
    * jest.retryTimes(numRetries, options) didn't applicable in this case.
    * */
    await waitFor(10000);

    const result = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
    openIdConfiguration = result.body;
});

describe("MFA", () => {
    describe('MFA rate limit test', () => {
        let user1;

        it("throw mfa_request_limit_exceed after 2 request", async () => {
            const clientId = smsTestApp.settings.oauth.clientId;

            user1 = await buildCreateAndTestUser(domain.id, accessToken, 1);
            expect(user1).toBeDefined();

            const authResponse = await initiateLoginFlow(clientId, openIdConfiguration, domain);
            const postLogin = await login(authResponse, user1, clientId);

            // Post login authentication
            const authorize = await postLoginAuthentication(postLogin);

            expect(authorize.headers['location']).toBeDefined();
            expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/enroll`);

            const enrollMFA = await enrollMockFactor(authorize, mockFactor, domain);
            const authorize2 = await performGet(enrollMFA.headers['location'], '', {
                'Cookie': enrollMFA.headers['set-cookie']
            }).expect(302);

            expect(authorize2.headers['location']).toBeDefined();
            expect(authorize2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

            /**
             * The number of the get requests is based on the gateway gravtitee.yml 'mfa_rate' configuration
             * These assertions will fail or need to be updated if 'mfa_rate' configuration is changed
             */
            const expectedCode = [200, 200, 302];
            for (const responseCode of expectedCode) {
                const rateLimitException = await performGet(authorize2.headers['location'], '', {
                    'Cookie': authorize2.headers['set-cookie']
                }).expect(responseCode);

                if (responseCode === 302) {
                    expect(rateLimitException.headers['location']).toBeDefined();
                    expect(rateLimitException.headers['location']).toContain('request_limit_error=mfa_request_limit_exceed');
                }
            }
        });

        afterAll(async () => {
            await deleteUser(domain.id, accessToken, user1.id);
        });
    });

    describe('brute force test', () => {
        let user2;

        it('should throw brute force exception', async () => {
            const clientId = bruteForceTestApp.settings.oauth.clientId;

            user2 = await buildCreateAndTestUser(domain.id, accessToken, 1);
            expect(user2).toBeDefined();

            const authResponse = await initiateLoginFlow(clientId, openIdConfiguration, domain);
            const postLogin = await login(authResponse, user2, clientId);

            const authorize = await postLoginAuthentication(postLogin);

            expect(authorize.headers['location']).toBeDefined();
            expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/enroll`);

            const enrollMFA = await enrollMockFactor(authorize, mockFactor, domain);

            const authorize2 = await performGet(enrollMFA.headers['location'], '', {
                'Cookie': enrollMFA.headers['set-cookie']
            }).expect(302);

            expect(authorize2.headers['location']).toBeDefined();
            expect(authorize2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

            await performGet(authorize2.headers['location'], '', {
                'Cookie': authorize2.headers['set-cookie']
            }).expect(200);

            const expectedErrorMessage = [
                {expected: 'mfa_challenge_failed'},
                {expected: 'mfa_challenge_failed'},
                {expected: 'verify_attempt_error=maximum_verify_limit'}
            ];

            //mfa challenge post
            const authResult2 = await extractXsrfTokenAndActionResponse(authorize2);
            const invalidCode = 999;
            for (let i = 0; i < expectedErrorMessage.length; i++) {
                const failedVerification = await verifyMockFactor(authResult2, invalidCode);
                expect(failedVerification.headers['location']).toBeDefined();
                expect(failedVerification.headers['location']).toContain(expectedErrorMessage[i].expected);
            }

            //now wait 1 second as per the configuration
            await waitFor(mfaChallengeAttemptsResetTime * 1000);

            const successfulVerification = await verifyMockFactor(authResult2, validMFACode);
            await logoutUser(openIdConfiguration.end_session_endpoint, successfulVerification);
        });

        afterAll(async () => {
            await deleteUser(domain.id, accessToken, user2.id);
        });
    });

    describe('TOTP authentication', () => {
        let totpUser;
        let sharedSecret;
        it('should login using authenticator code', async () => {
            const clientId = totpApp.settings.oauth.clientId;
            totpUser = await buildCreateAndTestUser(domain.id, accessToken, 1);
            const authResponse = await initiateLoginFlow(clientId, openIdConfiguration, domain);
            const postLogin = await login(authResponse, totpUser, clientId);

            let authorize = await performGet(postLogin.headers['location'], '', {
                'Cookie': postLogin.headers['set-cookie']
            }).expect(302);
            expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/enroll`);

            const enrolOtp = await enrollOtpFactor(totpUser, authorize, totpFactor, domain);
            const authorize2 = await performGet(enrolOtp.headers['location'], '', {
                'Cookie': enrolOtp.headers['set-cookie']
            }).expect(302);
            expect(authorize2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

            sharedSecret = enrolOtp.sharedSecret;
            const totp = new TOTP({issuer: 'Gravitee.io', secret: sharedSecret});
            const totpToken = totp.generate();
            await verifyFactorFailure(authorize2, totpFactor);
            const successfulVerification = await verifyFactor(authorize2, totpToken, totpFactor);
            await logoutUser(openIdConfiguration.end_session_endpoint, successfulVerification);
        });
        it('should issue challenge when factor already enrolled', async () => {
            const clientId = totpApp.settings.oauth.clientId;

            const authResponse = await initiateLoginFlow(clientId, openIdConfiguration, domain);
            const postLogin = await login(authResponse, totpUser, clientId);

            let authorise = await performGet(postLogin.headers['location'], '', {
                'Cookie': postLogin.headers['set-cookie']
            }).expect(302);
            expect(authorise.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

            const totp = new TOTP({issuer: 'Gravitee.io', secret: sharedSecret});
            const totpToken = totp.generate();
            await verifyFactorFailure(authorise, totpFactor);
            const successfulVerification = await verifyFactor(authorise, totpToken, totpFactor);
            await logoutUser(openIdConfiguration.end_session_endpoint, successfulVerification);
        });
        afterAll(async () => {
            await deleteUser(domain.id, accessToken, totpUser.id);
        });
    });

    describe('email factor test', () => {
        let user3;

        it('should send email', async () => {
            const clientId = emailTestApp.settings.oauth.clientId;

            user3 = await buildCreateAndTestUser(domain.id, accessToken, 1);
            expect(user3).toBeDefined();

            const authResponse = await initiateLoginFlow(clientId, openIdConfiguration, domain);
            const postLogin = await login(authResponse, user3, clientId);

            const authorize = await performGet(postLogin.headers['location'], '', {
                'Cookie': postLogin.headers['set-cookie']
            }).expect(302);

            expect(authorize.headers['location']).toBeDefined();
            expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/enroll`);

            const enrollMFA = await enrollEmailFactor(user3, authorize, emailFactor, domain);

            const authorize2 = await performGet(enrollMFA.headers['location'], '', {
                'Cookie': enrollMFA.headers['set-cookie']
            }).expect(302);

            expect(authorize2.headers['location']).toBeDefined();
            expect(authorize2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);
            await performGet(authorize2.headers['location'], '', {
                'Cookie': authorize2.headers['set-cookie']
            }).expect(200);

            const email = await getLastEmail();
            expect(email).toBeDefined();
            const verificationCode = email.contents[0].data.match(".*class=\"otp-code\".*<span[^>]*>.([0-9]{6}).<\\/span>")[1];
            expect(verificationCode).toBeDefined();
            await clearEmails();

            const successfulVerification = await verifyFactor(authorize2, verificationCode, emailFactor);
            await logoutUser(openIdConfiguration.end_session_endpoint, successfulVerification);
        });

        afterAll(async () => {
            await deleteUser(domain.id, accessToken, user3.id);
        });
    });

    describe('recovery code factor test', () => {
        let user4;
        let validRecoveryCode;

        it("recovery code factor should be present", async () => {
            const clientId = recoveryCodeTestApp.settings.oauth.clientId;

            user4 = await buildCreateAndTestUser(domain.id, accessToken, 1);
            expect(user4).toBeDefined();

            const authResponse = await initiateLoginFlow(clientId, openIdConfiguration, domain);
            const postLogin = await login(authResponse, user4, clientId);

            // Post login authentication
            const authorize = await performGet(postLogin.headers['location'], '', {
                'Cookie': postLogin.headers['set-cookie']
            }).expect(302);

            expect(authorize.headers['location']).toBeDefined();
            expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/enroll`);

            const enrollMFA = await enrollMockFactor(authorize, mockFactor, domain);

            const authorize2 = await performGet(enrollMFA.headers['location'], '', {
                'Cookie': enrollMFA.headers['set-cookie']
            }).expect(302);

            expect(authorize2.headers['location']).toBeDefined();
            expect(authorize2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

            const successfulVerification = await verifyFactor(authorize2, validMFACode, mockFactor);

            const recoveryCodeRedirectUri = await performGet(successfulVerification.headers['location'], '', {
                'Cookie': successfulVerification.headers['set-cookie']
            }).expect(302);

            const recoveryCodeResponse = await performGet(recoveryCodeRedirectUri.headers['location'], '', {
                'Cookie': recoveryCodeRedirectUri.headers['set-cookie']
            }).expect(200);

            expect(recoveryCodeResponse.text).toBeDefined();
            validRecoveryCode = getARecoveryCode(recoveryCodeResponse.text);
            expect(validRecoveryCode).toBeDefined();

            const authorize3 = await postRecoveryCodeForm(recoveryCodeRedirectUri, recoveryCodeResponse);
            const callbackGetResponse = await performGet(authorize3.headers['location'], '', {
                'Cookie': authorize3.headers['set-cookie']
            }).expect(302);

            const callbackResponse = await extractXsrfTokenAndActionResponse(callbackGetResponse);
            const response = await performGet(callbackResponse.action, '', {
                'Cookie': callbackResponse.headers['set-cookie']
            }).expect(200);

            expect(response).toBeDefined();

            await logoutUser(openIdConfiguration.end_session_endpoint, response);
        });

        it("login using recovery code", async () => {
            const clientId = recoveryCodeTestApp.settings.oauth.clientId;

            const authResponse = await initiateLoginFlow(clientId, openIdConfiguration, domain);
            const postLogin = await login(authResponse, user4, clientId);

            // Post login authentication
            const authorize = await performGet(postLogin.headers['location'], '', {
                'Cookie': postLogin.headers['set-cookie']
            }).expect(302);

            expect(authorize.headers['location']).toBeDefined();
            expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

            const mfaChallenge = await performGet(authorize.headers['location'], '', {
                'Cookie': authorize.headers['set-cookie']
            }).expect(200);

            const alternativeUrl = await getAlternativeUrl(mfaChallenge)
            expect(alternativeUrl).toBeDefined();

            await performGet(alternativeUrl, '', {
                'Cookie': mfaChallenge.headers['set-cookie']
            }).expect(200);

            const recoveryCodeChallenge = await postAlternativeMFAUrl(alternativeUrl, mfaChallenge, recoveryCodeFactor);
            expect(recoveryCodeChallenge).toBeDefined();

            const successfulVerification = await verifyFactor(recoveryCodeChallenge, validRecoveryCode, recoveryCodeFactor);
            await logoutUser(openIdConfiguration.end_session_endpoint, successfulVerification);
        })

        afterAll(async () => {
            await deleteUser(domain.id, accessToken, user4.id);
        });

    });
});

afterAll(async () => {
    if (domain && domain.id) {
        await deleteDomain(domain.id, accessToken);
    }
});

const initiateLoginFlow = async (clientId, openIdConfiguration, domain) => {
    const params = `?response_type=code&client_id=${clientId}&redirect_uri=https://auth-nightly.gravitee.io/myApp/callback`;

    const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);
    const loginLocation = authResponse.headers['location'];

    expect(loginLocation).toBeDefined();
    expect(loginLocation).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/login`);
    expect(loginLocation).toContain(`client_id=${clientId}`);

    return authResponse;
}

const login = async (authResponse, user, clientId) => {
    const loginResult = await extractXsrfTokenAndActionResponse(authResponse);
    return await performFormPost(loginResult.action, '', {
            "X-XSRF-TOKEN": loginResult.token,
            "username": user.username,
            "password": "SomeP@ssw0rd",
            "client_id": clientId
        }, {
            'Cookie': loginResult.headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded'
        }
    ).expect(302);
}

const enrollMockFactor = async (authorize, factor, domain) => {
    const authResult = await extractXsrfTokenAndActionResponse(authorize);
    const enrollMFA = await performPost(authResult.action, '', {
            "factorId": factor.id,
            "user_mfa_enrollment": true,
            "X-XSRF-TOKEN": authResult.token
        },
        {
            'Cookie': authResult.headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded'
        }).expect(302);

    expect(enrollMFA.headers['location']).toBeDefined();
    expect(enrollMFA.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

    return enrollMFA;
}

const createSMTPResource = async (domain, accessToken) => {
    const smtp = await createResource(domain.id, accessToken, {
        "type": "smtp-am-resource",
        "configuration": "{\"host\":\"localhost\",\"port\":5025,\"from\":\"admin@test.com\",\"protocol\":\"smtp\",\"authentication\":false,\"startTls\":false}",
        "name": "FakeSmtp"
    });

    expect(smtp.id).not.toBeNull();

    return smtp;
}

const createEmailFactor = async (smtpResource, domain, accessToken) => {
    const factor = await createFactor(domain.id, accessToken, {
        "type": "email-am-factor",
        "factorType": "EMAIL",
        "configuration": `{\"graviteeResource\":\"${smtpResource.id}\",\"returnDigits\":6}`,
        "name": "Email"
    });

    expect(factor).toBeDefined();
    expect(factor).not.toBeNull();

    return factor;
}

const enrollEmailFactor = async (user, authorize, emailFactor, domain) => {
    const authResult = await extractXsrfTokenAndActionResponse(authorize);
    const enrollMFA = await performPost(authResult.action, '', {
            "factorId": emailFactor.id,
            "email": user.email,
            "sharedSecret": sharedSecret,
            "user_mfa_enrollment": true,
            "X-XSRF-TOKEN": authResult.token
        },
        {
            'Cookie': authResult.headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded'
        }).expect(302);

    expect(enrollMFA.headers['location']).toBeDefined();
    expect(enrollMFA.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

    return enrollMFA;
}

const createOtpFactor = async () => {
    return await createFactor(domain.id, accessToken, {
        "type": "otp-am-factor",
        "factorType": "TOTP",
        "configuration": "{\"issuer\":\"Gravitee.io\",\"algorithm\":\"HmacSHA1\",\"timeStep\":\"30\",\"returnDigits\":\"6\"}",
        "name": "totp Factor"
    });
}

const enrollOtpFactor = async (user, authResponse, otpFactor, domain) => {
    const headers = authResponse.headers['set-cookie'] ? {'Cookie': authResponse.headers['set-cookie']} : {};
    const result = await performGet(authResponse.headers['location'], '', headers).expect(200);
    const dom = cheerio.load(result.text);
    const xsrfToken = dom("[name=X-XSRF-TOKEN]").val();
    const action = dom("form").attr('action');

    expect(xsrfToken).toBeDefined();
    expect(action).toBeDefined();

    const factors = dom('script').text().split('\n').find(line => line.trim().startsWith('const factors'));
    const totpSharedSecret = JSON.parse(factors.substring(factors.indexOf('=')+1).replaceAll(';', ''))[0].enrollment.key;

    const enrollMfa = await performPost(action, '', {
            "factorId": otpFactor.id,
            "sharedSecret": totpSharedSecret,
            "user_mfa_enrollment": true,
            "X-XSRF-TOKEN": xsrfToken
        },
        {
            'Cookie': result.headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded'
        });
    expect(enrollMfa.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

    return {headers: enrollMfa.headers, sharedSecret: totpSharedSecret};
}

const createSMSResource = async (validMFACode, domain, accessToken) => {
    const smsResource = await createResource(domain.id, accessToken, {
        "type": "mock-mfa-am-resource",
        "configuration": `{\"code\":\"${validMFACode}\"}`,
        "name": "Mock Resource"
    });

    expect(smsResource).toBeDefined();
    expect(smsResource).not.toBeNull();
    expect(smsResource.id).not.toBeNull();

    return smsResource;
}

const createMockFactor = async (smsResource, domain, accessToken) => {
    const factor = await createFactor(domain.id, accessToken, {
        "type": "mock-am-factor",
        "factorType": "MOCK",
        "configuration": `{\"graviteeResource\":\"${smsResource.id}\"}`,
        "name": "Mock Factor"
    });

    expect(factor).toBeDefined();
    expect(factor).not.toBeNull();

    return factor;
}

const createMfaApp = async (domain, accessToken, factors: Array<number>) => {
    const application = await createApplication(domain.id, accessToken, {
        "name": faker.company.bsBuzz(),
        "type": "WEB",
        "clientId": faker.internet.domainWord()
    }).then(app => updateApplication(domain.id, accessToken, {
        "settings": {
            "oauth": {
                "redirectUris": ["https://auth-nightly.gravitee.io/myApp/callback"],
                "scopeSettings": [{"scope": "openid", "defaultScope": true}, {
                    "scope": "openid",
                    "defaultScope": true
                }]
            }
        },
        "account": {
            "inherited": false,
            "mfaChallengeAttemptsDetectionEnabled": true,
            "mfaChallengeMaxAttempts": 2,
            "mfaChallengeAttemptsResetTime": mfaChallengeAttemptsResetTime
        },
        "identityProviders": [
            {"identity": `default-idp-${domain.id}`, "priority": -1}
        ],
        "factors": factors
    }, app.id));
    expect(application.settings.oauth.clientId).toBeDefined();
    return application;
}

const createBruteForceTestApp = async (smsFactor, domain, accessToken, mfaChallengeAttemptsResetTime) => {
    const application = await createApplication(domain.id, accessToken, {
        "name": "mfa-bruteforce-test",
        "type": "WEB",
        "clientId": "mfa-bruteforce-test-id"
    }).then(app => updateApplication(domain.id, accessToken, {
        "settings": {
            "oauth": {
                "redirectUris": ["https://auth-nightly.gravitee.io/myApp/callback"],
                "scopeSettings": [{"scope": "openid", "defaultScope": true}, {
                    "scope": "openid",
                    "defaultScope": true
                }]
            },
            "account": {
                "inherited": false,
                "mfaChallengeAttemptsDetectionEnabled": true,
                "mfaChallengeMaxAttempts": 2,
                "mfaChallengeAttemptsResetTime": mfaChallengeAttemptsResetTime
            }
        },
        "identityProviders": [
            {"identity": `default-idp-${domain.id}`, "priority": -1}
        ],
        "factors": [
            smsFactor.id
        ]
    }, app.id));

    expect(application).toBeDefined();
    expect(application.settings.oauth.clientId).toBeDefined();

    return application;
}

const createRecoveryCodeFactor = async (domain, accessToken) => {
    const factor = await createFactor(domain.id, accessToken, {
        "type": "recovery-code-am-factor",
        "factorType": "Recovery Code",
        "configuration": "{\"digit\":5,\"count\":6}",
        "name": "Recovery Code"
    });

    expect(factor).toBeDefined();
    expect(factor).not.toBeNull();

    return factor;
}

const getARecoveryCode = (text) => {
    const dom = cheerio.load(text);
    return dom(".code-item").get(0).childNodes[0].data;
}

const postRecoveryCodeForm = async (recoveryCodeRedirectUri, recoveryCodeResponse) => {
    const dom = cheerio.load(recoveryCodeResponse.text);
    const xsrfToken = dom("[name=X-XSRF-TOKEN]").val();
    const callback = await performPost(recoveryCodeRedirectUri.headers['location'], '', {
            "X-XSRF-TOKEN": xsrfToken
        },
        {
            'Cookie': recoveryCodeResponse.headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded'
        }).expect(302);

    expect(callback.headers['location']).toBeDefined();
    expect(callback.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

    return callback;
}

const getAlternativeUrl = async (mfaChallenge) => {
    const dom = cheerio.load(mfaChallenge.text);
    return dom("a").attr("href");
}

const postAlternativeMFAUrl = async (alternativeUrl, mfaChallenge, factor) => {
    const dom = cheerio.load(mfaChallenge.text);
    const xsrfToken = dom("[name=X-XSRF-TOKEN]").val();
    const challenge = await performPost(alternativeUrl, '', {
            "factorId": factor.id,
            "X-XSRF-TOKEN": xsrfToken
        },
        {
            'Cookie': mfaChallenge.headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded'
        }).expect(302);

    expect(challenge.headers['location']).toBeDefined();
    expect(challenge.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

    return challenge;
}

const verifyFactor = async (challenge, code, factor) => {
    const challengeResponse = await extractXsrfTokenAndActionResponse(challenge);
    const successfulVerification = await performPost(challengeResponse.action, '', {
            "factorId": factor.id,
            "code": code,
            "X-XSRF-TOKEN": challengeResponse.token
        },
        {
            'Cookie': challengeResponse.headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded'
        }).expect(302);

    expect(successfulVerification.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);
    return successfulVerification;
}

const verifyFactorFailure = async (challenge, factor) => {
    const challengeResponse = await extractXsrfTokenAndActionResponse(challenge);
    const failedVerification = await performPost(challengeResponse.action, '', {
            "factorId": factor.id,
            "code": 123456,
            "X-XSRF-TOKEN": challengeResponse.token
        },
        {
            'Cookie': challengeResponse.headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded'
        }).expect(302);

    expect(failedVerification.headers['location']).toContain('error=mfa_challenge_failed');
    return failedVerification;
}

const verifyMockFactor = async (authResult, code) => {
    return await performPost(authResult.action, '', {
            "factorId": mockFactor.id,
            "code": code,
            "X-XSRF-TOKEN": authResult.token
        },
        {
            'Cookie': authResult.headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded'
        }).expect(302);
}

const postLoginAuthentication = async (postLogin) => {
    return await performGet(postLogin.headers['location'], '', {
        'Cookie': postLogin.headers['set-cookie']
    }).expect(302);
}