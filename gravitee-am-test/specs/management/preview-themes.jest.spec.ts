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
import * as faker from 'faker';
import {afterAll, beforeAll, expect} from '@jest/globals';
import {requestAdminAccessToken} from "@management-commands/token-management-commands";
import {createDomain, deleteDomain, startDomain} from "@management-commands/domain-management-commands";
import { preview } from "@management-commands/form-management-commands";

const path = require("path");
const fs = require("fs");

global.fetch = fetch;

let accessToken;
let domain;

let customDraftTheme = {
    name: faker.address.country(),
    locale: faker.address.countryCode(),
    primaryTextColorHex: "#FFFFFF",
    primaryButtonColorHex: "#000000",
    secondaryTextColorHex: "#FFFFFF",
    secondaryButtonColorHex: "#000000"
};

beforeAll(async () => {
    const adminTokenResponse = await requestAdminAccessToken();
    accessToken = adminTokenResponse.body.access_token;
    expect(accessToken).toBeDefined()

    const createdDomain = await createDomain(accessToken, "domain-themes-preview", faker.company.catchPhraseDescriptor());
    expect(createdDomain).toBeDefined();
    expect(createdDomain.id).toBeDefined();

    const domainStarted = await startDomain(createdDomain.id, accessToken);
    expect(domainStarted).toBeDefined();
    expect(domainStarted.id).toEqual(createdDomain.id);

    domain = domainStarted;
});

async function testRequestPreview(template: String, content: String, theme?: any) {
    const previewResult = await preview(domain.id, accessToken, {
        type: 'FORM',
        template: template, 
        content: content,
        theme: theme
    })
    expect(previewResult).toBeDefined();
    return previewResult;
}

const TEMPLATE_DIRECTORY = "../../../gravitee-am-gateway/gravitee-am-gateway-handler/gravitee-am-gateway-handler-core/src/main/resources/webroot/views/";

describe("Testing preview form api...", () => {
    describe("With LOGIN template", () => {
        const file = path.join(__dirname,
             TEMPLATE_DIRECTORY, 
             "login.html");
        const content = fs.readFileSync(file, "utf8", function(err: any, data: any) {
            return data;
        });

        requestPreview("login", content);
    });
    
    describe("With ERROR template", () => {
        const file = path.join(__dirname,
            TEMPLATE_DIRECTORY, 
             "error.html");
        const content = fs.readFileSync(file, "utf8", function(err: any, data: any) {
            return data;
        });

        requestPreview("error", content);
    });
    
    describe("With RESET_PASSWORD template", () => {
        const file = path.join(__dirname,
            TEMPLATE_DIRECTORY, 
             "error.html");
        const content = fs.readFileSync(file, "utf8", function(err: any, data: any) {
            return data;
        });

        requestPreview("reset_password", content);
    });
    
    describe("With OAUTH2_USER_CONSENT template", () => {
        const file = path.join(__dirname,
            TEMPLATE_DIRECTORY, 
             "oauth2_user_consent.html");
        const content = fs.readFileSync(file, "utf8", function(err: any, data: any) {
            return data;
        });

        requestPreview("oauth2_user_consent", content);
    });

    describe("With MFA_CHALLENGE_ALTERNATIVES template", () => {
        const file = path.join(__dirname,
            TEMPLATE_DIRECTORY, 
             "mfa_challenge_alternatives.html");
        const content = fs.readFileSync(file, "utf8", function(err: any, data: any) {
            return data;
        });

        requestPreview("mfa_challenge_alternatives", content);
    });
    
    describe("With MFA_RECOVERY_CODE template", () => {
        const file = path.join(__dirname,
            TEMPLATE_DIRECTORY, 
             "mfa_recovery_code.html");
        const content = fs.readFileSync(file, "utf8", function(err: any, data: any) {
            return data;
        });

        requestPreview("mfa_recovery_code", content);
    });
    
    describe("With WEBAUTHN_REGISTER template", () => {
        const file = path.join(__dirname,
            TEMPLATE_DIRECTORY, 
             "webauthn_register.html");
        const content = fs.readFileSync(file, "utf8", function(err: any, data: any) {
            return data;
        });

        requestPreview("webauthn_register", content);
    });

    describe("With WEBAUTHN_LOGIN template", () => {
        const file = path.join(__dirname,
            TEMPLATE_DIRECTORY, 
             "webauthn_login.html");
        const content = fs.readFileSync(file, "utf8", function(err: any, data: any) {
            return data;
        });

        requestPreview("webauthn_login", content);
    });

    describe("With IDENTIFIER_FIRST_LOGIN template", () => {
        const file = path.join(__dirname,
            TEMPLATE_DIRECTORY, 
             "identifier_first_login.html");
        const content = fs.readFileSync(file, "utf8", function(err: any, data: any) {
            return data;
        });

        requestPreview("identifier_first_login", content);
    });

    describe("With MFA_CHALLENGE template", () => {
        const file = path.join(__dirname,
            TEMPLATE_DIRECTORY, 
             "mfa_challenge.html");
        const content = fs.readFileSync(file, "utf8", function(err: any, data: any) {
            return data;
        });

        requestPreview("mfa_challenge", content);
    });

    describe("With MFA_ENROLL template", () => {
        const file = path.join(__dirname,
            TEMPLATE_DIRECTORY, 
             "mfa_enroll.html");
        const content = fs.readFileSync(file, "utf8", function(err: any, data: any) {
            return data;
        });

        requestPreview("mfa_enroll", content);
    });

    describe("With FORGOT_PASSWORD template", () => {
        const file = path.join(__dirname,
            TEMPLATE_DIRECTORY, 
             "forgot_password.html");
        const content = fs.readFileSync(file, "utf8", function(err: any, data: any) {
            return data;
        });

        requestPreview("forgot_password", content);
    });

    describe("With REGISTRATION_CONFIRMATION template", () => {
        const file = path.join(__dirname,
            TEMPLATE_DIRECTORY, 
             "registration_confirmation.html");
        const content = fs.readFileSync(file, "utf8", function(err: any, data: any) {
            return data;
        });

        requestPreview("registration_confirmation", content);
    });

    describe("With REGISTRATION template", () => {
        const file = path.join(__dirname,
            TEMPLATE_DIRECTORY, 
             "registration.html");
        const content = fs.readFileSync(file, "utf8", function(err: any, data: any) {
            return data;
        });

        requestPreview("registration", content);
    });

});


describe("Testing invalid preview form api...", () => {
    describe("With unknown variable into the template", () => {
        it('must return an error', async () => {
            try {
                await testRequestPreview("login", `<!DOCTYPE html>
                <html lang="en" xmlns:th="http://www.thymeleaf.org">
                <head>
                    <!-- Favicon and touch icons -->
                    <link rel="shortcut icon" th:href="\${unknowntheme.faviconUrl}">
                </head>
                <body>
                <div class="container">
                    <img class="logo" th:src="\${unknown} ?: @{assets/images/gravitee-logo.svg}">
                </div>
                </body>
                </html>
                    `);
            } catch (e) {
                expect(e.response.status).toEqual(400);
            }
        });
    });

    describe("With unknown template", () => {
        it('must return an error', async () => {
            try {
                await testRequestPreview("unknown", "content");
            } catch (e) {
                expect(e.response.status).toEqual(400);
            }
        });
    });

});
    
afterAll(async () => {
    if (domain && domain.id) {
        await deleteDomain(domain.id, accessToken);
    }
});

function requestPreview(template: String, content: String) {
    it('must render the form with default theme', async () => {
        let preview = await testRequestPreview(template, content);
        expect(preview).toBeDefined;
        expect(preview['content']).toBeDefined;
        expect(preview['content']).toContain("#6A4FF7");
    });

    it('must render the form with draft theme', async () => {
        let preview = await testRequestPreview(template, content, customDraftTheme);
        expect(preview).toBeDefined;
        expect(preview['content']).toBeDefined;
        expect(preview['content']).toContain("#FFFFFF");
    });
}

