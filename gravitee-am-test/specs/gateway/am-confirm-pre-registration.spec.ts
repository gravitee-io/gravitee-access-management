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
import {clearEmails, getLastEmail} from "@utils-commands/email-commands";

import {jest, afterAll, beforeAll, expect} from "@jest/globals";
import {requestAdminAccessToken} from "@management-commands/token-management-commands";
import {createDomain, deleteDomain, startDomain} from "@management-commands/domain-management-commands";
import { createUser, getUser } from "@management-commands/user-management-commands";
import { extractXsrfToken, performFormPost } from "@gateway-commands/oauth-oidc-commands";

global.fetch = fetch;

let domain;
let accessToken;
let confirmationLink;
let createdUser;

jest.setTimeout(200000)

beforeAll(async () => {
    const adminTokenResponse = await requestAdminAccessToken();
    accessToken = adminTokenResponse.body.access_token;
    expect(accessToken).toBeDefined();
    const createdDomain = await createDomain(accessToken, "pre-registration", "test user pre-registration");
    expect(createdDomain).toBeDefined();
    expect(createdDomain.id).toBeDefined();
    domain = createdDomain;

    const domainStarted = await startDomain(domain.id, accessToken);
    expect(domainStarted).toBeDefined();
    expect(domainStarted.id).toEqual(domain.id);
    domain = domainStarted;
    await new Promise((r) => setTimeout(r, 6000));
});


describe("AM - User Pre-Registration", () => {

    const preRegisteredUser = {
        "username": "preregister",
        "firstName": "preregister",
        "lastName": "preregister",
        "email": "preregister@acme.fr",
        "preRegistration": true
    };

    it("must pre-register a user", async () => {
        createdUser = await createUser(domain.id, accessToken, preRegisteredUser);
        expect(createdUser).toBeDefined();
        // Pre registered user are not enabled.
        // They have to provide a password first.
        expect(createdUser.enabled).toBeFalsy();
    });

    describe("User", () => {
        it("must received an email",async () => {
            await new Promise((r) => setTimeout(r, 1000))
            confirmationLink = (await getLastEmail()).extractLink();
            expect(confirmationLink).toBeDefined();
            await clearEmails();
        });

        it("must confirm the registration by providing a password",async () => {
            const url = new URL(confirmationLink);
            const resetPwdToken = url.searchParams.get('token');
            const baseUrlConfirmRegister = confirmationLink.substring(0, confirmationLink.indexOf('?'));

            const {headers, token: xsrfToken} = await extractXsrfToken(baseUrlConfirmRegister, '?token='+resetPwdToken);

            const postConfirmRegistration = await performFormPost(baseUrlConfirmRegister, '', {
                    "X-XSRF-TOKEN": xsrfToken,
                    "token": resetPwdToken,
                    "password": "#CoMpL3X-P@SsW0Rd"
                }, {
                    'Cookie': headers['set-cookie'],
                    'Content-type': 'application/x-www-form-urlencoded'
                }
            ).expect(302);

            expect(postConfirmRegistration.headers['location']).toBeDefined();
            expect(postConfirmRegistration.headers['location']).toContain('success=registration_completed');

        });

        it("must be enabled", async () => {
            let user = await getUser(domain.id, accessToken, createdUser.id);
            expect(user).toBeDefined();
            // Pre registered user are not enabled.
            // They have to provide a password first.
            expect(user.enabled).toBeTruthy();
        });
    });
});

afterAll(async () => {
    if (domain && domain.id) {
        await deleteDomain(domain.id, accessToken);
    }
});
