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
import {createDomain, deleteDomain, startDomain, waitFor} from "@management-commands/domain-management-commands";
import {createResource} from "@management-commands/resource-management-commands";
import {createFactor} from "@management-commands/factor-management-commands";
import {createApplication, updateApplication} from "@management-commands/application-management-commands";
import faker from "faker";
import {extractXsrfTokenAndActionResponse, getWellKnownOpenIdConfiguration, logoutUser, performFormPost, performGet, performPost} from "@gateway-commands/oauth-oidc-commands";
import {buildCreateAndTestUser} from "@management-commands/user-management-commands";
import {initiateLoginFlow} from "@gateway-commands/login-commands";


global.fetch = fetch;

let accessToken;
let domain;
let mockFactor;
let smsResource;
let openIdConfiguration;
let appSettings;

const validMFACode = '333333';

jest.setTimeout(200000);

beforeAll(async () => {
    const adminTokenResponse = await requestAdminAccessToken();
    accessToken = adminTokenResponse.body.access_token;
    expect(accessToken).toBeDefined();
    domain = await createDomain(accessToken, 'mfa-split-test-domain', 'test mfa split');

    smsResource = await createSMSResource(validMFACode, domain, accessToken);
    mockFactor = await createMockFactor(smsResource, domain, accessToken);

    domain = await startDomain(domain.id, accessToken);

    appSettings = await createMfaApp(domain, accessToken, [mockFactor.id], true, 'REQUIRED', true, 'REQUIRED');

    /*
   * it is intentional to call setTimeout after creating apps. Cannot avoid this timeout call.
   * At this point I haven't found a function which is similar to retry until specific http code is returned.
   * jest.retryTimes(numRetries, options) didn't applicable in this case.
   * */
    await waitFor(10000);

    const result = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
    openIdConfiguration = result.body;

});

describe('MFA enroll enabled, challenge disabled', () => {
    it('should enroll when enroll is required', async () => {
        const clientId = appSettings.settings.oauth.clientId;

        const user1 = await buildCreateAndTestUser(domain.id, accessToken, 1);
        expect(user1).toBeDefined();

        const authResponse = await initiateLoginFlow(clientId, openIdConfiguration, domain);
        const postLogin = await login(authResponse, user1, clientId);

        const authorize = await performGet(postLogin.headers['location'], '', {
            Cookie: postLogin.headers['set-cookie'],
        }).expect(302);

        expect(authorize.headers['location']).toBeDefined();
        expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/enroll`);

        const enrollMFA = await enrollMockFactor(authorize, mockFactor, domain);

        const authorize2 = await performGet(enrollMFA.headers['location'], '', {
            Cookie: enrollMFA.headers['set-cookie'],
        }).expect(302);

        expect(authorize2.headers['location']).toBeDefined();
        expect(authorize2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);
        await performGet(authorize2.headers['location'], '', {
            Cookie: authorize2.headers['set-cookie'],
        }).expect(200);

        const authResult2 = await extractXsrfTokenAndActionResponse(authorize2);
        const successfulVerification = await verifyMockFactor(authResult2, validMFACode);
        await logoutUser(openIdConfiguration.end_session_endpoint, successfulVerification);
    });

    /*it('should enroll when enroll is optional', async () => {
        const clientId = appEnrollEnabledRequiredChallengeDisabled.settings.oauth.clientId;

        const user2 = await buildCreateAndTestUser(domain.id, accessToken, 1);
        expect(user2).toBeDefined();

        const authResponse = await initiateLoginFlow(clientId, openIdConfiguration, domain);
        const postLogin = await login(authResponse, user2, clientId);

        const authorize = await performGet(postLogin.headers['location'], '', {
            Cookie: postLogin.headers['set-cookie'],
        }).expect(302);

        expect(authorize.headers['location']).toBeDefined();
        expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/enroll`);

        const enrollMFA = await enrollMockFactor(authorize, mockFactor, domain);

        const authorize2 = await performGet(enrollMFA.headers['location'], '', {
            Cookie: enrollMFA.headers['set-cookie'],
        }).expect(302);

        expect(authorize2.headers['location']).toBeDefined();
        expect(authorize2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);
        await performGet(authorize2.headers['location'], '', {
            Cookie: authorize2.headers['set-cookie'],
        }).expect(200);

        const authResult2 = await extractXsrfTokenAndActionResponse(authorize2);
        const successfulVerification = await verifyMockFactor(authResult2, validMFACode);
        await logoutUser(openIdConfiguration.end_session_endpoint, successfulVerification);
    });*/

    afterAll(async () => {
        if (domain && domain.id) {
            await deleteDomain(domain.id, accessToken);
        }
    });
});

const createSMSResource = async (validMFACode, domain, accessToken) => {
    const smsResource = await createResource(domain.id, accessToken, {
        type: 'mock-mfa-am-resource',
        configuration: `{\"code\":\"${validMFACode}\"}`,
        name: 'Mock Resource',
    });

    expect(smsResource).toBeDefined();
    expect(smsResource).not.toBeNull();
    expect(smsResource.id).not.toBeNull();

    return smsResource;
};

const createMfaApp = async (domain, accessToken, factors: Array<number>, enrollActive, enrollType, challengeActive, challengeType) => {
    const application = await createApplication(domain.id, accessToken, {
        name: faker.company.bsBuzz(),
        type: 'WEB',
        clientId: faker.internet.domainWord(),
        redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback']
    }).then((app) =>
        updateApplication(
            domain.id,
            accessToken,
            {
                settings: {
                    oauth: {
                        redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
                        scopeSettings: [
                            {scope: 'openid', defaultScope: true}
                        ],
                    },
                    mfa: {
                        factor: {
                            defaultFactorId: factors[0],
                            applicationFactors: factors.map((i) => {
                                return {
                                    id: i,
                                    selectionRule: ''
                                } as any
                            })
                        },
                        enroll: {
                            active: enrollActive,
                            forceEnrollment: true,
                            type: enrollType
                        },
                        challenge: {
                            active: challengeActive,
                            type: challengeType
                        }
                    }
                },
                identityProviders: [{identity: `default-idp-${domain.id}`, priority: -1}],
                factors: factors,
            },
            app.id,
        ).then(updatedApp => {
            // restore the clientSecret coming from the create order
            updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
            return updatedApp;
        }),
    );
    expect(application.settings.oauth.clientId).toBeDefined();
    return application;
};

const createMockFactor = async (smsResource, domain, accessToken) => {
    const factor = await createFactor(domain.id, accessToken, {
        type: 'mock-am-factor',
        factorType: 'MOCK',
        configuration: `{\"graviteeResource\":\"${smsResource.id}\"}`,
        name: 'Mock Factor',
    });

    expect(factor).toBeDefined();
    expect(factor).not.toBeNull();

    return factor;
};

const enrollMockFactor = async (authorize, factor, domain) => {
    const authResult = await extractXsrfTokenAndActionResponse(authorize);
    const enrollMFA = await performPost(
        authResult.action,
        '',
        {
            factorId: factor.id,
            user_mfa_enrollment: true,
            'X-XSRF-TOKEN': authResult.token,
        },
        {
            Cookie: authResult.headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded',
        },
    ).expect(302);

    expect(enrollMFA.headers['location']).toBeDefined();
    expect(enrollMFA.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

    return enrollMFA;
};

const verifyMockFactor = async (authResult, code) => {
    return await performPost(
        authResult.action,
        '',
        {
            factorId: mockFactor.id,
            code: code,
            'X-XSRF-TOKEN': authResult.token,
        },
        {
            Cookie: authResult.headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded',
        },
    ).expect(302);
};

const login = async (authResponse, user, clientId) => {
    const loginResult = await extractXsrfTokenAndActionResponse(authResponse);
    return await performFormPost(
        loginResult.action,
        '',
        {
            'X-XSRF-TOKEN': loginResult.token,
            username: user.username,
            password: 'SomeP@ssw0rd',
            client_id: clientId,
        },
        {
            Cookie: loginResult.headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded',
        },
    ).expect(302);
};
