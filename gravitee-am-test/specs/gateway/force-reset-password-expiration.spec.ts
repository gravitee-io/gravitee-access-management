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
import fetch from 'cross-fetch';
import * as faker from 'faker';
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import { createDomain, deleteDomain, startDomain, waitForDomainSync } from '@management-commands/domain-management-commands';
import { buildCreateAndTestUser, updateUserStatus } from '@management-commands/user-management-commands';

import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {getWellKnownOpenIdConfiguration, performGet, performPost} from '@gateway-commands/oauth-oidc-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import {
    assignPasswordPolicyToIdp,
    createPasswordPolicy,
    updatePasswordPolicy
} from '@management-commands/password-policy-management-commands';
import {initiateLoginFlow, login} from '@gateway-commands/login-commands';
import {TEST_USER} from './oidc-idp/common';

global.fetch = fetch;

let accessToken;
let domain;
let defaultIdp;
let client;
let oidc;
let policy;

let userWithExpiredPassword1;
let userWithExpiredPassword2;

jest.setTimeout(200000);

beforeAll(async () => {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    const createdDomain = await createDomain(accessToken, 'force-reset-password-exp-domain-enabled', faker.company.catchPhraseDescriptor());
    expect(createdDomain).toBeDefined();
    expect(createdDomain.id).toBeDefined();

    const idpSet = await getAllIdps(createdDomain.id, accessToken);
    defaultIdp = idpSet.values().next().value;

    client = await createTestApp('webapp', createdDomain, accessToken, 'WEB', {
        settings: {
            oauth: {
                redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
                grantTypes: ['authorization_code'],
                scopeSettings: [],
            },
        },
        identityProviders: [{ identity: defaultIdp.id, priority: 0 }],
    });

    policy = await createPasswordPolicy(createdDomain.id, accessToken, {
        name: 'default-to-assign',
        resetPasswordOnExpiration: true,
        expiryDuration: 1
    }).then(policy => assignPasswordPolicyToIdp(createdDomain.id, accessToken, defaultIdp.id, policy.id));


    const domainStarted = await startDomain(createdDomain.id, accessToken);
    expect(domainStarted).toBeDefined();
    expect(domainStarted.id).toEqual(createdDomain.id);

    domain = domainStarted;
    await new Promise((r) => setTimeout(r, 10000));

    const result = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
    oidc = result.body;

    const today = new Date();

    const twoDaysAgo = new Date(today);
    twoDaysAgo.setDate(today.getDate() - 2);

    userWithExpiredPassword1 = await buildCreateAndTestUser(domain.id, accessToken, 1, false, "SomeP@sswo0rd", twoDaysAgo);
    userWithExpiredPassword2 = await buildCreateAndTestUser(domain.id, accessToken, 2, false, "SomeP@sswo0rd", twoDaysAgo);
});

afterAll(async () => {
    if (domain?.id) {
        await deleteDomain(domain.id, accessToken);
    }
});

describe('when resetPasswordOnExpiration is enabled', () => {

    it('and user with expired password is trying to log in should be forced to reset password', async () => {
        const clientId = client.settings.oauth.clientId;
        const authResponse = await initiateLoginFlow(clientId, oidc, domain)
        const postLogin = await login(authResponse, userWithExpiredPassword1.username, clientId, "SomeP@sswo0rd");
        const authResponse2 = await performGet(postLogin.headers['location'], '', {
            Cookie: postLogin.headers['set-cookie'],
        }).expect(302);

        const resetPasswordLocation = authResponse2.headers['location'];

        expect(resetPasswordLocation).toBeDefined();
        expect(resetPasswordLocation).toContain("/resetPassword");
    });

});

describe('when resetPasswordOnExpiration is disabled', () => {

    it('and user with expired password is trying to log in should see an error', async () => {

        await updatePasswordPolicy(domain.id, accessToken, policy.passwordPolicy, {
            name: 'default-to-assign',
            expiryDuration: 1,
            resetPasswordOnExpiration: false,
        })
        await new Promise((r) => setTimeout(r, 10000));

        const clientId = client.settings.oauth.clientId;
        const authResponse = await initiateLoginFlow(clientId, oidc, domain)
        const postLogin = await login(authResponse, userWithExpiredPassword2.username, clientId, "SomeP@sswo0rd");

        const postLoginLocation = postLogin.headers['location'];

        expect(postLoginLocation).toBeDefined();
        expect(postLoginLocation).toContain("error_code=account_password_expired");
    });

});
