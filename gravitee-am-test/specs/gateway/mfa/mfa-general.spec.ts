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
import {afterAll, beforeAll, expect, jest} from '@jest/globals';
import {
    Domain,
    enableDomain,
    initClient,
    initDomain,
    removeDomain,
    TestSuiteContext
} from './fixture/mfa-setup-fixture';
import {withRetry} from '@utils-commands/retry';
import {getWellKnownOpenIdConfiguration} from '@gateway-commands/oauth-oidc-commands';
import fetch from 'cross-fetch';
import {extractDomAttr, extractDomValue} from './fixture/mfa-extract-fixture';
import {followUpGet, get, postForm, processMfaEndToEnd} from './fixture/mfa-flow-fixture';
import {waitFor} from "@management-commands/domain-management-commands";

global.fetch = fetch;
jest.setTimeout(200000);

const domain: Domain = {
    admin: {
        username: 'admin',
        password: 'adminadmin',
    },
    domain: {
        domainHrid: 'mfa-test-domain-general',
    },
} as Domain;

function defaultApplicationSettings() {
    return {
        factors: [],
        settings: {
            mfa: {
                factor: {
                    defaultFactorId: null,
                    applicationFactors: [],
                },
                stepUpAuthenticationRule: '',
                stepUpAuthentication: {active: false, stepUpAuthenticationRule: ''},
                adaptiveAuthenticationRule: '',
                rememberDevice: {active: false, skipRememberDevice: false},
                enrollment: {forceEnrollment: false},
                enroll: {active: false, enrollmentSkipActive: false, forceEnrollment: false, type: 'required'},
                challenge: {active: false, challengeRule: '', type: 'required'},
            },
        },
    };
}

beforeAll(async () => {
    await initDomain(domain, 7);

    let settings;

    settings = defaultApplicationSettings();
    const noFactorClient = await initClient(domain, 'no-factors-client', settings);

    settings = {
        ...defaultApplicationSettings(),
        settings: {
            mfa: {
                factor: {
                    defaultFactorId: domain.domain.factors[0].id,
                    applicationFactors: [domain.domain.factors[0]],
                },
                stepUpAuthenticationRule: '{{ false }}',
                stepUpAuthentication: {active: true, stepUpAuthenticationRule: '{{ false }}'},
            },
        },
    }
    const stepUpNegativeClient1 = await initClient(domain, 'step-up-positive-1', settings);

    settings = {
        ...defaultApplicationSettings(),
        settings: {
            mfa: {
                factor: {
                    defaultFactorId: domain.domain.factors[0].id,
                    applicationFactors: [domain.domain.factors[0]],
                },
                stepUpAuthenticationRule: '',
                stepUpAuthentication: {active: false, stepUpAuthenticationRule: ''},
            },
        },
    };
    const stepUpOffClient = await initClient(domain, 'step-up-off-1', settings);

    settings = {
        ...defaultApplicationSettings(),
        settings: {
            mfa: {
                factor: {
                    defaultFactorId: domain.domain.factors[0].id,
                    applicationFactors: [domain.domain.factors[0]],
                },
                stepUpAuthenticationRule: '{{ true }}',
                stepUpAuthentication: {active: true, stepUpAuthenticationRule: '{{ true }}'},
                enrollment: {forceEnrollment: true},
                enroll: {active: true, enrollmentSkipActive: false, forceEnrollment: true, type: 'required'},
                challenge: {active: true, challengeRule: '', type: 'required'},
            },
        },
    };
    const stepUpTrueClient2 = await initClient(domain, 'step-up-positive-2', settings);

    settings = {
        ...defaultApplicationSettings(),
        settings: {
            mfa: {
                factor: {
                    defaultFactorId: domain.domain.factors[0].id,
                    applicationFactors: [domain.domain.factors[0]],
                },
            },
        },
    };
    const withFactorsClient = await initClient(domain, 'with-factors-client-1', settings);

    settings = {
        ...defaultApplicationSettings(),
        settings: {
            mfa: {
                factor: {
                    defaultFactorId: domain.domain.factors[0].id,
                    applicationFactors: [
                        {id: domain.domain.factors[0].id, selectionRule: '{{ false }}'},
                        {id: domain.domain.factors[1].id, selectionRule: '{{ false }}'},
                    ],
                },
                enroll: {active: true, enrollmentSkipActive: false, forceEnrollment: false, type: 'required'},
            },
        },
    };
    const enrollmentTrueClient = await initClient(domain, 'enrollment-true-1', settings);

    settings = defaultApplicationSettings();
    settings.settings.mfa.factor = {
        defaultFactorId: domain.domain.factors[0].id,
        applicationFactors: [domain.domain.factors[0]],
    }
    settings.settings.mfa.stepUpAuthentication =  {active: true, stepUpAuthenticationRule: '{{ true }}'};
    settings.settings.mfa.stepUpAuthenticationRule =  '{{ true }}';
    const stepUpTrueClient3 = await initClient(domain, 'step-up-positive-3', settings);

    const oidc = await enableDomain(domain)
        .then(() => waitFor(3000))
        .then(() => withRetry(() => getWellKnownOpenIdConfiguration(domain.domain.domainHrid).expect(200)))

    noFactorsCtx = new TestSuiteContext(domain, noFactorClient, domain.domain.users[0], oidc.body.authorization_endpoint)
    stepUpNegativeCtx = new TestSuiteContext(domain, stepUpNegativeClient1, domain.domain.users[1], oidc.body.authorization_endpoint)
    stepUpOffCtx = new TestSuiteContext(domain, stepUpOffClient, domain.domain.users[2], oidc.body.authorization_endpoint)
    stepUpPositiveCtx2 = new TestSuiteContext(domain, stepUpTrueClient2, domain.domain.users[3], oidc.body.authorization_endpoint)
    withFactorsCtx = new TestSuiteContext(domain, withFactorsClient, domain.domain.users[4], oidc.body.authorization_endpoint)
    enrollmentTrueCtx = new TestSuiteContext(domain, enrollmentTrueClient, domain.domain.users[5], oidc.body.authorization_endpoint)
    stepUpPositiveChallengeDisabledCtx = new TestSuiteContext(domain, stepUpTrueClient3, domain.domain.users[6], oidc.body.authorization_endpoint)

});

let noFactorsCtx: TestSuiteContext;
let stepUpNegativeCtx: TestSuiteContext;
let stepUpOffCtx: TestSuiteContext;
let stepUpPositiveCtx2: TestSuiteContext;
let withFactorsCtx: TestSuiteContext;
let enrollmentTrueCtx: TestSuiteContext;
let stepUpPositiveChallengeDisabledCtx: TestSuiteContext;

afterAll(async () => {
    await removeDomain(domain);
});

describe('With disabled factors', () => {
    it('should omit MFA flow', async () => {
        const ctx = noFactorsCtx;
        const authResponse = await get(ctx.clientAuthUrl, 302);
        const loginPage = await followUpGet(authResponse, 200);

        let xsrf = extractDomValue(loginPage, '[name=X-XSRF-TOKEN]');
        let action = extractDomAttr(loginPage, 'form', 'action');

        const loginPostResponse = await postForm(
            action,
            {
                'X-XSRF-TOKEN': xsrf,
                username: ctx.user.username,
                password: ctx.user.password,
                rememberMe: 'off',
                client_id: ctx.client.clientId,
            },
            {
                Cookie: loginPage.headers['set-cookie'],
                'Content-type': 'application/x-www-form-urlencoded',
            },
            302);

        const authResponseFinal = await followUpGet(loginPostResponse, 302);

        expect(authResponseFinal.headers['location']).toBeDefined();
        expect(authResponseFinal.headers['location']).toContain(ctx.client.redirectUris[0]);
        expect(authResponseFinal.headers['location']).toContain('code=');
    });
});

describe('With one enabled factor should omit MFA flow', () => {
    it('when stepUp=false', async () => {
        const ctx = stepUpNegativeCtx;

        const authResponse = await get(ctx.clientAuthUrl, 302);
        const loginPage = await followUpGet(authResponse, 200);

        let xsrf = extractDomValue(loginPage, '[name=X-XSRF-TOKEN]');
        let action = extractDomAttr(loginPage, 'form', 'action');

        const loginPostResponse = await postForm(
            action,
            {
                'X-XSRF-TOKEN': xsrf,
                username: ctx.user.username,
                password: ctx.user.password,
                rememberMe: 'off',
                client_id: ctx.client.clientId,
            },
            {
                Cookie: loginPage.headers['set-cookie'],
                'Content-type': 'application/x-www-form-urlencoded',
            },
            302
        );

        const authResponseFinal = await followUpGet(loginPostResponse, 302);

        expect(authResponseFinal.headers['location']).toBeDefined();
        expect(authResponseFinal.headers['location']).toContain(ctx.client.redirectUris[0]);
        expect(authResponseFinal.headers['location']).toContain('code=');
    });

    it('when stepUp is disabled', async () => {
        const ctx = stepUpOffCtx;
        const authResponse = await get(ctx.clientAuthUrl, 302);
        const loginPage = await followUpGet(authResponse, 200);

        let xsrf = extractDomValue(loginPage, '[name=X-XSRF-TOKEN]');
        let action = extractDomAttr(loginPage, 'form', 'action');

        const loginPostResponse = await postForm(
            action,
            {
                'X-XSRF-TOKEN': xsrf,
                username: ctx.user.username,
                password: ctx.user.password,
                rememberMe: 'off',
                client_id: ctx.client.clientId,
            },
            {
                Cookie: loginPage.headers['set-cookie'],
                'Content-type': 'application/x-www-form-urlencoded',
            }, 302);

        const authResponseFinal = await followUpGet(loginPostResponse, 302);

        expect(authResponseFinal.headers['location']).toBeDefined();
        expect(authResponseFinal.headers['location']).toContain(ctx.client.redirectUris[0]);
        expect(authResponseFinal.headers['location']).toContain('code=');
    });
});

describe('With active session, when stepUp is true, on authorization', () => {
    it('should Challenge', async () => {
        const ctx = stepUpPositiveCtx2;

        const session = await processMfaEndToEnd(ctx);
        const authResponse = await get(ctx.clientAuthUrl, 302, {Cookie: session.cookie});

        expect(authResponse.headers['location']).toBeDefined();
        expect(authResponse.headers['location']).toContain('/challenge');
    });
});

describe('With enabled factors and disabled enrollment and disabled challenge', () => {
    it('should stop MFA flow', async () => {
        const ctx = withFactorsCtx;

        const authResponse = await get(ctx.clientAuthUrl, 302);
        const loginPage = await followUpGet(authResponse, 200);

        let xsrf = extractDomValue(loginPage, '[name=X-XSRF-TOKEN]');
        let action = extractDomAttr(loginPage, 'form', 'action');

        const loginPostResponse = await postForm(
            action,
            {
                'X-XSRF-TOKEN': xsrf,
                username: ctx.user.username,
                password: ctx.user.password,
                rememberMe: 'off',
                client_id: ctx.client.clientId,
            },
            {
                Cookie: loginPage.headers['set-cookie'],
                'Content-type': 'application/x-www-form-urlencoded',
            }, 302);

        const finalLocationResponse = await followUpGet(loginPostResponse, 302);

        expect(finalLocationResponse.headers['location']).toBeDefined();
        expect(finalLocationResponse.headers['location']).toContain(ctx.client.redirectUris[0]);
        expect(finalLocationResponse.headers['location']).toContain('code=');
    });
});

describe('With enabled factors and but failing selection rule', () => {
    it('only default factor should be visible', async () => {
        const ctx = enrollmentTrueCtx;

        const authResponse = await get(ctx.clientAuthUrl, 302);
        const loginPage = await followUpGet(authResponse, 200);

        let xsrf = extractDomValue(loginPage, '[name=X-XSRF-TOKEN]');
        let action = extractDomAttr(loginPage, 'form', 'action');

        const loginPostResponse = await postForm(
            action,
            {
                'X-XSRF-TOKEN': xsrf,
                username: ctx.user.username,
                password: ctx.user.password,
                rememberMe: 'off',
                client_id: ctx.client.clientId,
            },
            {
                Cookie: loginPage.headers['set-cookie'],
                'Content-type': 'application/x-www-form-urlencoded',
            }, 302);

        const enrollLocationResponse = await followUpGet(loginPostResponse, 302);
        const enrollmentPage = await followUpGet(enrollLocationResponse, 200);

        const factorId = extractDomValue(enrollmentPage, '[name=factorId]');

        expect(factorId).toBe(domain.domain.factors[0].id);
    });
});

describe('With active session, when stepUp is true, with challenge disabled, on authorization', () => {
    it('should Challenge', async () => {
        const ctx = stepUpPositiveChallengeDisabledCtx;
        const session = await processMfaEndToEnd(ctx);

        const authResponse = await get(ctx.clientAuthUrl, 302, {Cookie: session.cookie});
        expect(authResponse.headers['location']).toBeDefined();
        expect(authResponse.headers['location']).toContain("challenge");
    });
});
