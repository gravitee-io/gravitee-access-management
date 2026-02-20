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

import {afterAll, beforeAll, expect} from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
    createDomain,
    DomainOidcConfig,
    patchDomain,
    startDomain,
    waitFor,
    waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { extractXsrfTokenAndHref, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { initiateLoginFlow } from '@gateway-commands/login-commands';
import { uniqueName } from '@utils-commands/misc';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { Domain } from '@management-models/Domain';
import { setup } from '../test-fixture';
import {clearEmails, getLastEmail} from '@utils-commands/email-commands';

setup(20000);

interface UserPem {
    user: any;
}

let userWithoutCred: UserPem;

let accessToken;
let domain: Domain;
let oidc: DomainOidcConfig;
let app;

beforeAll(async () => {
    accessToken = await requestAdminAccessToken();

    await createDomain(accessToken, uniqueName('magic-link-domain', true), 'Magic Link')
        .then((d) =>
            patchDomain(d.id, accessToken, {
                loginSettings: {
                    magicLinkAuthEnabled: true,
                },
            }),
        )
        .then((d) =>
            startDomain(d.id, accessToken)
                .then(waitForDomainStart)
                .then((result) => {
                    domain = result.domain;
                    oidc = result.oidcConfig;
                }),
        );

    const idpSet = await getAllIdps(domain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;

    app = await createApplication(domain.id, accessToken, {
        name: 'test',
        type: 'WEB',
        clientId: 'test',
        clientSecret: 'test',
        redirectUris: ['https://test'],
    }).then((app) =>
        updateApplication(
            domain.id,
            accessToken,
            {
                settings: {
                    oauth: {
                        redirectUris: ['https://test'],
                        grantTypes: ['authorization_code'],
                    },
                },
                identityProviders: [{ identity: defaultIdp.id, priority: -1 }],
            },
            app.id,
        ).then((updatedApp) => {
            updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
            return updatedApp;
        }),
    );

    userWithoutCred = await createUser(domain.id, accessToken, {
        firstName: 'with',
        lastName: 'out',
        email: `without@test.com`,
        username: 'without',
        password: 'Password123!',
        client: app.id,
        source: defaultIdp.id,
    }).then((user) => {
        return {
            user: user
        };
    });

    await waitFor(5000);
});

describe.skip('Should display information that the email has been sent', () => {
    it('with the email address assigned to the user', async () => {
        const clientId = app.settings.oauth.clientId;

        const authResponse = await initiateLoginFlow(clientId, oidc, domain, 'code', 'https://test');
        const loginPage = await extractXsrfTokenAndHref(authResponse, 'magicLinkLinkButton');
        const magicLinkResponse = await performPost(loginPage.action, '', 'email=without@test.com', { Cookie: loginPage.headers['set-cookie'] });

        expect(magicLinkResponse.status).toBe(200);
        expect(magicLinkResponse.statusCode).toBe(200);
    });
    it('with an email address not assigned to the user', async () => {
        const clientId = app.settings.oauth.clientId;

        const authResponse = await initiateLoginFlow(clientId, oidc, domain, 'code', 'https://test');
        const loginPage = await extractXsrfTokenAndHref(authResponse, 'magicLinkLinkButton');
        const magicLinkResponse = await performPost(loginPage.action, '', 'email=no-address@test.com', { Cookie: loginPage.headers['set-cookie'] });

        expect(magicLinkResponse.status).toBe(200);
        expect(magicLinkResponse.statusCode).toBe(200);
    });
});
describe.skip('Should display information that the email is invalid', () => {
    it('with invalid e-mail address', async () => {
        const clientId = app.settings.oauth.clientId;

        const authResponse = await initiateLoginFlow(clientId, oidc, domain, 'code', 'https://test');
        const loginPage = await extractXsrfTokenAndHref(authResponse, 'magicLinkLinkButton');
        const magicLinkResponse = await performPost(loginPage.action, '', 'email=test', { Cookie: loginPage.headers['set-cookie'] });

        expect(magicLinkResponse.status).toBe(302);
        expect(magicLinkResponse.statusCode).toBe(302);

        const rawUrl =
            magicLinkResponse.body?.url ??
            magicLinkResponse.headers?.location ??
            (magicLinkResponse as any).request?.url;

        expect(rawUrl).toBeDefined();

        const url = new URL(String(rawUrl), 'http://localhost');
        expect(url.searchParams.has('error')).toBe(true);
        expect(url.searchParams.has('error_description')).toBe(true);
        expect(url.searchParams.get('error_description')).toBe('Value [test] is not a valid email.');
    });
});
describe.skip('Should display generic information', () => {
    it('when token is invalid', async () => {
        const clientId = app.settings.oauth.clientId;

        const authResponse = await initiateLoginFlow(clientId, oidc, domain, 'code', 'https://test');
        const invalidToken = 'eyJraWQiiJkZWZhdWx0LWdyYXZpdGVlLUFNLWtleSIsInR5cCI6IkpXVCIsImFsZyI6IkhTMjU2In0.eyJpc3MiOiJodHRwczovL2dyYXZpdGVlLmFtIiwic3ViIjoiMjMxNTc5NzktZWE0OC00ZTA2LTk1NzktNzllYTQ4OGUwNjA0IiwiYXVkIjoiNmFhODNmOGItMmQ0OS00YjM1LWE4M2YtOGIyZDQ5YWIzNWYwIiwic2Vzc2lvbl9pZCI6IjIxMWViZGIzM2M1MjY4ZGE0YzkxYWE1Zjg3ZDM5MDcwIiwiZXhwIjoxNzcxNTg5OTc5LCJpYXQiOjE3NzE1ODkwNzl9.9B2umT-uT3qU3fmItNwGIxduOH6bRS8jnlKoAMhpWUQ';
        const requestUrl = `${process.env.AM_GATEWAY_URL}/${domain.hrid}/magic-link/auth?response_type=code&amp;redirect_uri=https://test&amp;client_id=test&amp;token=${invalidToken}`;
        const magicLinkResponse = await performGet(requestUrl, '', { Cookie: authResponse.headers['set-cookie'] });

        const rawUrl =
            magicLinkResponse.body?.url ??
            magicLinkResponse.headers?.location ??
            (magicLinkResponse as any).request?.url;

        expect(rawUrl).toBeDefined();

        const url = new URL(String(rawUrl), 'http://localhost');
        expect(url.searchParams.has('error')).toBe(true);
        expect(url.searchParams.has('error_description')).toBe(true);
        expect(url.searchParams.get('error_description')).toBe('An unexpected error has occurred');
    });
});
describe.skip('Should authenticate user', () => {
    it('with valid token', async () => {
        const clientId = app.settings.oauth.clientId;

        const authResponse = await initiateLoginFlow(clientId, oidc, domain, 'code', 'https://test');
        const loginPage = await extractXsrfTokenAndHref(authResponse, 'magicLinkLinkButton');
        const magicLinkResponse = await performPost(loginPage.action, '', 'email=without@test.com', { Cookie: loginPage.headers['set-cookie'] });

        expect(magicLinkResponse.status).toBe(200);
        expect(magicLinkResponse.statusCode).toBe(200);

        const magicLink = (await getLastEmail(2000, 'without@test.com')).extractLink()
        await clearEmails(userWithoutCred.user.email);

        const verifyTokenResponse = await performGet(magicLink, '', {
            Cookie: magicLinkResponse.headers['set-cookie'],
        });
        const authorizeResponse = await performGet(verifyTokenResponse.headers['location'], '', {
            Cookie: verifyTokenResponse.headers['set-cookie'],
        });
        expect(authorizeResponse.headers['location']).toContain('code=');
        expect(authorizeResponse.headers['location']).toContain('https://test');
    });
});
describe.skip('Should not authenticate user', () => {
    it('if token has been used', async () => {
        const clientId = app.settings.oauth.clientId;

        const authResponse = await initiateLoginFlow(clientId, oidc, domain, 'code', 'https://test');
        const loginPage = await extractXsrfTokenAndHref(authResponse, 'magicLinkLinkButton');
        const magicLinkResponse = await performPost(loginPage.action, '', 'email=without@test.com', { Cookie: loginPage.headers['set-cookie'] });

        expect(magicLinkResponse.status).toBe(200);
        expect(magicLinkResponse.statusCode).toBe(200);

        const magicLink = (await getLastEmail(2000, 'without@test.com')).extractLink()
        await clearEmails(userWithoutCred.user.email);

        await performGet(magicLink, '', {
            Cookie: magicLinkResponse.headers['set-cookie'],
        });

        const verifyTokenResponse = await performGet(magicLink, '', {
            Cookie: magicLinkResponse.headers['set-cookie'],
        });

        const rawUrl =
            verifyTokenResponse.body?.url ??
            verifyTokenResponse.headers?.location ??
            (verifyTokenResponse as any).request?.url;

        expect(rawUrl).toBeDefined();

        const url = new URL(String(rawUrl), 'http://localhost');
        expect(url.searchParams.has('error')).toBe(true);
        expect(url.searchParams.has('error_description')).toBe(true);
        expect(url.searchParams.get('error_description')).toBe('Something went wrong, please try again');
    });
});