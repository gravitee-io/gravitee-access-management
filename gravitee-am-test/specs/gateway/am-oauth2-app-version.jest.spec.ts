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

global.fetch = fetch;

import {jest, afterAll, beforeAll, expect} from "@jest/globals";
import {requestAdminAccessToken} from "@management-commands/token-management-commands";
import {createDomain, deleteDomain, patchDomain, startDomain} from "@management-commands/domain-management-commands";
import {createIdp, deleteIdp, getAllIdps} from "@management-commands/idp-management-commands";
import {createScope} from "@management-commands/scope-management-commands";
import {createCertificate} from "@management-commands/certificate-management-commands";
import {buildCertificate} from "@api-fixtures/certificates";
import {
    createApplication, deleteApplication, patchApplication,
    renewApplicationSecrets,
    updateApplication
} from "@management-commands/application-management-commands";
import {
    extractXsrfTokenAndActionResponse,
    getWellKnownOpenIdConfiguration, logoutUser,
    performFormPost,
    performGet,
    performPost
} from "@gateway-commands/oauth-oidc-commands";
import {applicationBase64Token, getBase64BasicAuth} from "@gateway-commands/utils";

let domain;
let accessToken;
let idp;
let scope;
let certificate;
let application1;
let application2;
let application3;
let openIdConfiguration;

jest.setTimeout(200000)

beforeAll(async () => {
    const adminTokenResponse = await requestAdminAccessToken();
    accessToken = adminTokenResponse.body.access_token;
    expect(accessToken).toBeDefined();
    const createdDomain = await createDomain(accessToken, "oauth2-app-version", "test oauth2 authorization framework specifications");
    expect(createdDomain).toBeDefined();
    expect(createdDomain.id).toBeDefined();
    domain = createdDomain;
});

describe("OAuth2 - App version", () => {
    describe("Prepare", () => {
        it('must make the domain master', async () => {
            const patchedDomain = await patchDomain(domain.id, accessToken, {
                "master": true,
                "oidc": {
                    "clientRegistrationSettings": {
                        "allowLocalhostRedirectUri": true,
                        "allowHttpSchemeRedirectUri": true,
                        "allowWildCardRedirectUri": true,
                        "isDynamicClientRegistrationEnabled": false,
                        "isOpenDynamicClientRegistrationEnabled": false
                    }
                }
            });
            expect(patchedDomain).toBeDefined();
            expect(patchedDomain.id).toEqual(domain.id);
            expect(patchedDomain.master).toBeTruthy();
            expect(patchedDomain.oidc.clientRegistrationSettings).toBeDefined();
            expect(patchedDomain.oidc.clientRegistrationSettings.allowLocalhostRedirectUri).toBeTruthy();
            expect(patchedDomain.oidc.clientRegistrationSettings.allowHttpSchemeRedirectUri).toBeTruthy();
            expect(patchedDomain.oidc.clientRegistrationSettings.allowWildCardRedirectUri).toBeTruthy();
            expect(patchedDomain.oidc.clientRegistrationSettings.dynamicClientRegistrationEnabled).toBeFalsy();
            expect(patchedDomain.oidc.clientRegistrationSettings.openDynamicClientRegistrationEnabled).toBeFalsy();

            domain = patchedDomain;
        });

        it('must delete the default idp', async () => {
            await deleteIdp(domain.id, accessToken, "default-idp-" + domain.id);
            const idpSet = await getAllIdps(domain.id, accessToken);

            expect(idpSet.size).toEqual(0);
        });

        it('must create a new idp', async () => {
            idp = await createIdp(domain.id, accessToken, {
                "external": false,
                "type": "inline-am-idp",
                "domainWhitelist": [],
                "configuration": "{\"users\":[{\"firstname\":\"my-user\",\"lastname\":\"my-user-lastname\",\"username\":\"user\",\"password\":\"#CoMpL3X-P@SsW0Rd\"},{\"firstname\":\"Jensen\",\"lastname\":\"Barbara\",\"username\":\"jensen.barbara\",\"email\":\"jensen.barbara@mail.com\",\"password\":\"#CoMpL3X-P@SsW0Rd\"}]}",
                "name": "inmemory"
            });
            expect(idp).toBeDefined()
        });

        it('must create a new scope', async () => {
            scope = await createScope(domain.id, accessToken, {
                "key": "scope1",
                "name": "scope1",
                "description": "scope1"
            });
            expect(scope).toBeDefined()
        });

        it('must create a new certificate', async () => {
            certificate = await createCertificate(domain.id, accessToken, buildCertificate("rs256"));
            expect(certificate).toBeDefined()
        });

        it('must create a new application 1', async () => {
            application1 = await createApplication(domain.id, accessToken, {
                "name": "my-client 1",
                "type": "WEB",
                "clientId": "clientId-test-1"
            }).then(app => updateApplication(domain.id, accessToken, {
                "settings": {
                    "oauth": {
                        "redirectUris": [],
                        "grantTypes": ["authorization_code", "password", "refresh_token"],
                        "scopeSettings": [{"scope": scope.key, "defaultScope": true}, {
                            "scope": "openid",
                            "defaultScope": true
                        }]
                    }
                },
                "identityProviders": [
                    {"identity": idp.id, "priority": -1}
                ]
            }, app.id));

            expect(application1).toBeDefined()
        });

        it('must create a new application 2', async () => {
            application2 = await createApplication(domain.id, accessToken, {
                "name": "my-client 2",
                "type": "WEB",
                "clientId": "client-test-2"
            }).then(app => updateApplication(domain.id, accessToken, {
                "settings": {
                    "oauth": {
                        "redirectUris": ["http://localhost:4000/"],
                        "scopeSettings": [{"scope": "scope1", "defaultScope": true}]
                    }
                },
                "identityProviders": [
                    {"identity": idp.id, "priority": -1}
                ]
            }, app.id));
            expect(application2).toBeDefined()
        });

        it('renew client - must create a new application 3', async () => {
            application3 = await createApplication(domain.id, accessToken, {
                "name": "my-client 3",
                "type": "WEB"
            }).then(app => updateApplication(domain.id, accessToken, {
                "settings": {
                    "oauth": {
                        "grantTypes": ["client_credentials"],
                        "scopeSettings": [{"scope": "scope1", "defaultScope": true}]
                    }
                }
            }, app.id));

            expect(application3).toBeDefined()
        });

        it('must start a domain', async () => {
            const domainStarted = await startDomain(domain.id, accessToken);
            expect(domainStarted).toBeDefined();
            expect(domainStarted.id).toEqual(domain.id);
            domain = domainStarted;
            await new Promise((r) => setTimeout(r, 10000));
        });

        it('must reach well-known endpoint', async () => {
            const result = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);

            openIdConfiguration = result.body
            expect(openIdConfiguration.authorization_endpoint).toBeDefined();
            expect(openIdConfiguration.token_endpoint).toBeDefined();
            expect(openIdConfiguration.revocation_endpoint).toBeDefined();
            expect(openIdConfiguration.userinfo_endpoint).toBeDefined();
            expect(openIdConfiguration.registration_endpoint).toBeDefined();
            expect(openIdConfiguration.end_session_endpoint).toBeDefined();
            expect(openIdConfiguration.introspection_endpoint).toBeDefined();
        });
    });

    describe("OAuth2 - RFC 6746", () => {
        describe("Invalid Request", () => {
            it('must perform an invalid grant type token request', async () => {
                await performPost(openIdConfiguration.token_endpoint, '', "grant_type=unknown", {
                    'Content-type': 'application/x-www-form-urlencoded',
                    "Authorization": "Basic " + applicationBase64Token(application1)
                }).expect(400, {
                    error: 'unsupported_grant_type',
                    error_description: 'Unsupported grant type: unknown'
                });
            });
        });

        describe("Resource Owner Password Credential Grant", () => {
            it("must perform an invalid client token request - base64 error", async () => {
                await performPost(openIdConfiguration.token_endpoint, '',
                    "grant_type=password&username=admin&password=adminadmin&scope=openid",
                    {
                        'Content-type': 'application/x-www-form-urlencoded',
                        'Authorization': `Basic invalid`
                    }).expect(401);
            });

            it("must perform an invalid client token request", async () => {
                await performPost(openIdConfiguration.token_endpoint, '',
                    "grant_type=password&username=admin&password=adminadmin&scope=openid",
                    {
                        'Content-type': 'application/x-www-form-urlencoded',
                        'Authorization': "Basic " + getBase64BasicAuth("wrong-client-id", "wrong-secret-id")
                    }).expect(401);
            });

            it("must perform a no scope token request", async () => {
                const response = await performPost(openIdConfiguration.token_endpoint, '',
                    "grant_type=password&username=user&password=#CoMpL3X-P@SsW0Rd",
                    {
                        'Content-type': 'application/x-www-form-urlencoded',
                        'Authorization': "Basic " + applicationBase64Token(application2)
                    }).expect(200);

                const data = response.body;

                assertGeneratedToken(data, "scope1")
            });

            it("must perform a an invalid scope token request", async () => {
                await performPost(openIdConfiguration.token_endpoint, '',
                    "grant_type=password&username=user&password=#CoMpL3X-P@SsW0Rd&scope=unknown",
                    {
                        'Content-type': 'application/x-www-form-urlencoded',
                        'Authorization': "Basic " + applicationBase64Token(application1)
                    }).expect(400, {
                    error: 'invalid_scope',
                    error_description: 'Invalid scope(s): unknown'
                });
            });

            it("must perform a an empty scope token request", async () => {
                await performPost(openIdConfiguration.token_endpoint, '',
                    "grant_type=password&username=user&password=#CoMpL3X-P@SsW0Rd&scope=",
                    {
                        'Content-type': 'application/x-www-form-urlencoded',
                        'Authorization': "Basic " + applicationBase64Token(application1)
                    }).expect(200);
            });

            it("must perform an invalid user token request", async () => {
                await performPost(openIdConfiguration.token_endpoint, '',
                    "grant_type=password&username=admin&password=adminadmin&scope=openid",
                    {
                        'Content-type': 'application/x-www-form-urlencoded',
                        'Authorization': "Basic " + applicationBase64Token(application1)
                    }).expect(400, {
                    error: 'invalid_grant',
                    error_description: 'The credentials you entered are invalid'
                });
            });

            it("must generate a valid token for application1", async () => {
                const response = await performPost(openIdConfiguration.token_endpoint, '',
                    "grant_type=password&username=user&password=#CoMpL3X-P@SsW0Rd&scope=scope1",
                    {
                        'Content-type': 'application/x-www-form-urlencoded',
                        'Authorization': "Basic " + applicationBase64Token(application1)
                    }).expect(200);

                assertGeneratedToken(response.body, "scope1")
            });

            it("must generate a valid token for application2", async () => {
                const response = await performPost(openIdConfiguration.token_endpoint, '',
                    "grant_type=password&username=user&password=#CoMpL3X-P@SsW0Rd&scope=scope1",
                    {
                        'Content-type': 'application/x-www-form-urlencoded',
                        'Authorization': "Basic " + applicationBase64Token(application2)
                    }).expect(200);

                assertGeneratedToken(response.body, "scope1")
            });
        });
        describe("OAuth2 - RFC 6746 - Client credentials grant", () => {
            it('renew client - must generate a new token from application 3', async () => {
                const response = await performPost(openIdConfiguration.token_endpoint, '',
                    "grant_type=client_credentials",
                    {
                        'Content-type': 'application/x-www-form-urlencoded',
                        'Authorization': "Basic " + applicationBase64Token(application3)
                    });

                assertGeneratedToken(response.body, "scope1")
            });

            it('renew client - must renew secrets and test token', async () => {
                application3 = await renewApplicationSecrets(domain.id, accessToken, application3.id)
                    .then(async app => {
                        await new Promise((r) => setTimeout(r, 5000));
                        await performPost(openIdConfiguration.token_endpoint, '',
                            "grant_type=client_credentials",
                            {
                                'Content-type': 'application/x-www-form-urlencoded',
                                'Authorization': "Basic " + applicationBase64Token(application3)
                            }).expect(401);

                        const response = await performPost(openIdConfiguration.token_endpoint, '',
                            "grant_type=client_credentials",
                            {
                                'Content-type': 'application/x-www-form-urlencoded',
                                'Authorization': "Basic " + applicationBase64Token(app)
                            }).expect(200);

                        assertGeneratedToken(response.body, "scope1")
                        return app;
                    });
            });

            it('must perform invalid request', async () => {
                await performPost(openIdConfiguration.token_endpoint, '',
                    "grant_type=client_credentials",
                    {
                        'Content-type': 'application/x-www-form-urlencoded',
                        'Authorization': "Basic " + getBase64BasicAuth(application3.settings.oauth.clientId, "wrong-secret-id")
                    }).expect(401);
            });

            it('must perform a no scope request', async () => {
                const response = await performPost(openIdConfiguration.token_endpoint, '',
                    "grant_type=client_credentials",
                    {
                        'Content-type': 'application/x-www-form-urlencoded',
                        'Authorization': "Basic " + applicationBase64Token(application3)
                    }).expect(200);

                assertGeneratedToken(response.body, "scope1")
            });

            it('must perform an empty scope request', async () => {
                await performPost(openIdConfiguration.token_endpoint, '',
                    "grant_type=client_credentials&scope=",
                    {
                        'Content-type': 'application/x-www-form-urlencoded',
                        'Authorization': "Basic " + applicationBase64Token(application3)
                    }).expect(200);
            });

            it('must generate token', async () => {
                const response = await performPost(openIdConfiguration.token_endpoint, '',
                    "grant_type=client_credentials&scope=scope1&example_parameter=example_value",
                    {
                        'Content-type': 'application/x-www-form-urlencoded',
                        'Authorization': "Basic " + applicationBase64Token(application3)
                    }).expect(200);

                assertGeneratedToken(response.body, "scope1")
            });
        });

        describe("Authorization code Grant", () => {
            it("must handle unknown scope", async () => {
                const clientId = application2.settings.oauth.clientId;
                const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/&scope=unknown-scope`;
                const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);

                expect(authResponse.headers['location']).toBeDefined();
                expect(authResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/login`);
                expect(authResponse.headers['location']).toContain(`client_id=${clientId}`);

                const {headers, token, action} = await extractXsrfTokenAndActionResponse(authResponse);
                const postLogin = await performFormPost(action, '', {
                        "X-XSRF-TOKEN": token,
                        "username": "user",
                        "password": "#CoMpL3X-P@SsW0Rd",
                        "client_id": clientId
                    }, {
                        'Cookie': headers['set-cookie'],
                        'Content-type': 'application/x-www-form-urlencoded'
                    }
                ).expect(302);

                const errorRedirect = await performGet(postLogin.headers['location'], '', {
                    'Cookie': postLogin.headers['set-cookie']
                }).expect(302);

                expect(errorRedirect.headers['location']).toBeDefined();
                expect(errorRedirect.headers['location']).toContain(`error=invalid_scope`);
                expect(errorRedirect.headers['location']).toContain(`error_description=Invalid+scope%2528s%2529%253A+unknown-scope`);

                await logoutUser(openIdConfiguration.end_session_endpoint, postLogin)
                    .then(_ => deleteApplication(domain.id, accessToken, application3.id))
                    .then(_ => {
                        application3 = null
                    });
            });

            it("must handle consent with default scope", async () => {
                // Prepare Login Flow Parameters
                const clientId = application2.settings.oauth.clientId;
                const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/`;

                // Initiate the Login Flow
                const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);
                const loginLocation = authResponse.headers['location'];

                expect(loginLocation).toBeDefined();
                expect(loginLocation).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/login`);
                expect(loginLocation).toContain(`client_id=${clientId}`);

                // Redirect to /login
                const loginResult = await extractXsrfTokenAndActionResponse(authResponse);
                // Authentication
                const postLogin = await performFormPost(loginResult.action, '', {
                        "X-XSRF-TOKEN": loginResult.token,
                        "username": "user",
                        "password": "#CoMpL3X-P@SsW0Rd",
                        "client_id": clientId
                    }, {
                        'Cookie': loginResult.headers['set-cookie'],
                        'Content-type': 'application/x-www-form-urlencoded'
                    }
                ).expect(302);

                // Post authentication
                const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
                    'Cookie': postLogin.headers['set-cookie']
                }).expect(302);

                expect(postLoginRedirect.headers['location']).toBeDefined();
                expect(postLoginRedirect.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/consent`);

                // Redirect to /oauth/consent
                const consentResult = await extractXsrfTokenAndActionResponse(postLoginRedirect);

                // Post consent
                const postConsent = await performFormPost(consentResult.action, '', {
                        "X-XSRF-TOKEN": consentResult.token,
                        "scope.scope1": true,
                        "user_oauth_approval": true
                    }, {
                        'Cookie': consentResult.headers['set-cookie'],
                        'Content-type': 'application/x-www-form-urlencoded'
                    }
                ).expect(302);

                // Redirect to URI
                const postConsentRedirect = await performGet(postConsent.headers['location'], '', {
                    'Cookie': postLogin.headers['set-cookie']
                }).expect(302);

                expect(postConsentRedirect.headers['location']).toBeDefined();
                expect(postConsentRedirect.headers['location']).toContain("http://localhost:4000/?");
                expect(postConsentRedirect.headers['location']).toMatch(/code=[-_a-zA-Z0-9]+&?/);

                await logoutUser(openIdConfiguration.end_session_endpoint, postConsentRedirect);
            });

            it("must handle consent with scope expiry", async () => {
                // Prepare Login Flow Parameters
                const clientId = application2.settings.oauth.clientId;
                const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/`;

                // Initiate the Login Flow
                const authResponse = await createScope(domain.id, accessToken, {
                    "key": "test",
                    "name": "Test",
                    "description": "Scope test description",
                    "expiresIn": 2
                }).then(_ => patchApplication(domain.id, accessToken, {
                    "settings": {
                        "oauth": {
                            "scopeSettings": [{"scope": "scope1", "defaultScope": true}, {
                                "scope": "openid",
                                "scopeApproval": 2,
                                "defaultScope": true
                            }, {"scope": "test", "defaultScope": true}]
                        }
                    }
                }, application2.id))
                    .then(_ => new Promise((r) => setTimeout(r, 6000)))
                    .then(_ => performGet(openIdConfiguration.authorization_endpoint, params).expect(302));

                expect(authResponse.headers['location']).toBeDefined();
                expect(authResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/login`);
                expect(authResponse.headers['location']).toContain(`client_id=${clientId}`);

                // Redirect to /login
                const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

                // Authentication
                const postLogin = await performFormPost(loginResult.action, '', {
                        "X-XSRF-TOKEN": loginResult.token,
                        "username": "user",
                        "password": "#CoMpL3X-P@SsW0Rd",
                        "client_id": clientId
                    }, {
                        'Cookie': loginResult.headers['set-cookie'],
                        'Content-type': 'application/x-www-form-urlencoded'
                    }
                ).expect(302);

                // Post authentication redirect
                const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
                    'Cookie': postLogin.headers['set-cookie']
                }).expect(302);

                expect(postLoginRedirect.headers['location']).toBeDefined();
                expect(postLoginRedirect.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/consent`);

                // Redirect to /oauth/consent
                const consentResult = await extractXsrfTokenAndActionResponse(postLoginRedirect);

                // Post consent
                const postConsent = await performFormPost(consentResult.action, '', {
                        "X-XSRF-TOKEN": consentResult.token,
                        "scope.openid": true,
                        "scope.test": true,
                        "user_oauth_approval": true
                    }, {
                        'Cookie': consentResult.headers['set-cookie'],
                        'Content-type': 'application/x-www-form-urlencoded'
                    }
                ).expect(302);

                // Redirect to URI
                const postConsentRedirect = await performGet(postConsent.headers['location'], '', {
                    'Cookie': postLogin.headers['set-cookie']
                }).expect(302);

                expect(postConsentRedirect.headers['location']).toBeDefined();
                expect(postConsentRedirect.headers['location']).toContain("http://localhost:4000/?");
                expect(postConsentRedirect.headers['location']).toMatch(/code=[-_a-zA-Z0-9]+&?/);

                // Initiate the Login Flow again
                const authResponse2 = await new Promise((r) => setTimeout(r, 6000))
                    .then(_ => performGet(openIdConfiguration.authorization_endpoint, params, {
                        'Cookie': postConsentRedirect.headers['set-cookie']
                    }).expect(302));

                expect(authResponse2.headers['location']).toBeDefined();
                expect(authResponse2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/consent`);

                await logoutUser(openIdConfiguration.end_session_endpoint, postConsentRedirect)
                    .then(_ => patchApplication(domain.id, accessToken, {"settings": application2.settings}, application2.id))
                    .then(_ => new Promise((r) => setTimeout(r, 6000)));
            });

            it("must handle invalid client", async () => {
                // Prepare Login Flow Parameters
                const clientId = application2.settings.oauth.clientId;
                const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/`;

                // Initiate the Login Flow
                const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);

                expect(authResponse.headers['location']).toBeDefined();
                expect(authResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/login`);
                expect(authResponse.headers['location']).toContain(`client_id=${clientId}`);

                // Redirect to /login
                const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

                // Authentication
                const postLogin = await performFormPost(loginResult.action, '', {
                        "X-XSRF-TOKEN": loginResult.token,
                        "username": "user",
                        "password": "#CoMpL3X-P@SsW0Rd",
                        "client_id": clientId
                    }, {
                        'Cookie': loginResult.headers['set-cookie'],
                        'Content-type': 'application/x-www-form-urlencoded'
                    }
                ).expect(302);

                // Post authentication redirect
                const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
                    'Cookie': postLogin.headers['set-cookie']
                }).expect(302);

                const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
                expect(postLoginRedirect.headers['location']).toBeDefined();
                expect(postLoginRedirect.headers['location']).toContain("http://localhost:4000/?");
                expect(postLoginRedirect.headers['location']).toMatch(codePattern);

                const code = postLoginRedirect.headers['location'].match(codePattern)[1];
                const badGrantResponse = await performPost(
                    `${openIdConfiguration.token_endpoint}?grant_type=authorization_code&code=${code}&redirect_uri=http://localhost:4000/`,
                    '', null,
                    {
                        "Authorization": `Basic ${applicationBase64Token(application1)}`
                    }
                ).expect(400);

                expect(badGrantResponse.body.error).toEqual("invalid_grant");
                expect(badGrantResponse.body.error_description)
                    .toEqual(`The authorization code ${code} does not belong to the client ${application1.settings.oauth.clientId}.`);

                await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect);
            });

            it("must handle invalid redirect_uri", async () => {
                // Prepare Login Flow Parameters
                const clientId = application2.settings.oauth.clientId;
                const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/`;

                // Initiate the Login Flow
                const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);

                const loginLocation = authResponse.headers['location'];

                expect(loginLocation).toBeDefined();
                expect(loginLocation).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/login`);
                expect(loginLocation).toContain(`client_id=${clientId}`);

                // Redirect to /login
                const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

                // Authentication
                const postLogin = await performFormPost(loginResult.action, '', {
                        "X-XSRF-TOKEN": loginResult.token,
                        "username": "user",
                        "password": "#CoMpL3X-P@SsW0Rd",
                        "client_id": clientId
                    }, {
                        'Cookie': loginResult.headers['set-cookie'],
                        'Content-type': 'application/x-www-form-urlencoded'
                    }
                ).expect(302);

                // Post authentication redirect
                const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
                    'Cookie': postLogin.headers['set-cookie']
                }).expect(302);

                const redirectUri = postLoginRedirect.headers['location'];
                const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
                expect(redirectUri).toBeDefined();
                expect(redirectUri).toContain("http://localhost:4000/?");
                expect(redirectUri).toMatch(codePattern);

                const authorizationCode = redirectUri.match(codePattern)[1];
                const badGrantResponse = await performPost(
                    `${openIdConfiguration.token_endpoint}?grant_type=authorization_code&code=${authorizationCode}&redirect_uri=http://localhost:5000/`,
                    '',
                    null, {
                        "Authorization": "Basic " + applicationBase64Token(application2)
                    }
                ).expect(400);

                expect(badGrantResponse.body.error).toEqual("invalid_grant");
                expect(badGrantResponse.body.error_description).toEqual("Redirect URI mismatch.");

                await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect);
            });

            it("must handle with state parameter", async () => {
                // Prepare Login Flow Parameters
                const clientId = application2.settings.oauth.clientId;
                const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/&state=1234-5678-9876`;

                // Initiate the Login Flow
                const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);

                expect(authResponse.headers['location']).toBeDefined();
                expect(authResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/login`);
                expect(authResponse.headers['location']).toContain(`client_id=${clientId}`);

                // Redirect to /login
                const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

                // Authentication
                const postLogin = await performFormPost(loginResult.action, '', {
                        "X-XSRF-TOKEN": loginResult.token,
                        "username": "user",
                        "password": "#CoMpL3X-P@SsW0Rd",
                        "client_id": clientId
                    }, {
                        'Cookie': loginResult.headers['set-cookie'],
                        'Content-type': 'application/x-www-form-urlencoded'
                    }
                ).expect(302);

                // Post authentication redirect
                const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
                    'Cookie': postLogin.headers['set-cookie']
                }).expect(302);

                const redirectUri = postLoginRedirect.headers['location'];
                const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
                expect(redirectUri).toBeDefined();
                expect(redirectUri).toContain("http://localhost:4000/?");
                expect(redirectUri).toContain("state=");
                expect(redirectUri).toMatch(codePattern);

                const authorizationCode = redirectUri.match(codePattern)[1];

                const tokenResult = await performPost(
                    `${openIdConfiguration.token_endpoint}?grant_type=authorization_code&code=${authorizationCode}&redirect_uri=http://localhost:4000/`,
                    '',
                    null, {
                        "Authorization": "Basic " + applicationBase64Token(application2)
                    }
                ).expect(200);

                expect(tokenResult.body.access_token).toBeDefined();
                expect(tokenResult.body.token_type).toBeDefined();
                expect(tokenResult.body.token_type).toEqual("bearer");
                expect(tokenResult.body.expires_in).toBeDefined();
                expect(tokenResult.body.scope).toBeDefined();
                expect(tokenResult.body.scope).toEqual("scope1");

                await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect);
            });

            for (const codeChallengeMethod of ["S256", "plain"]) {
                it("must miss code_verifier PKCE - " + codeChallengeMethod, async () => {
                    const clientId = application2.settings.oauth.clientId;
                    const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/&state=1234-5678-9876` +
                        `&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=${codeChallengeMethod}`;

                    // Initiate the Login Flow
                    const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);

                    expect(authResponse.headers['location']).toBeDefined();
                    expect(authResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/login`);
                    expect(authResponse.headers['location']).toContain(`client_id=${clientId}`);

                    const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

                    // Authentication
                    const postLogin = await performFormPost(loginResult.action, '', {
                            "X-XSRF-TOKEN": loginResult.token,
                            "username": "user",
                            "password": "#CoMpL3X-P@SsW0Rd",
                            "client_id": clientId
                        }, {
                            'Cookie': loginResult.headers['set-cookie'],
                            'Content-type': 'application/x-www-form-urlencoded'
                        }
                    ).expect(302);

                    // Post authentication redirect
                    const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
                        'Cookie': postLogin.headers['set-cookie']
                    }).expect(302);

                    const redirectUri = postLoginRedirect.headers['location'];
                    const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
                    expect(redirectUri).toBeDefined();
                    expect(redirectUri).toContain("http://localhost:4000/?");
                    expect(redirectUri).toContain("state=");
                    expect(redirectUri).toMatch(codePattern);

                    const authorizationCode = redirectUri.match(codePattern)[1];

                    const badGrantResponse = await performPost(
                        `${openIdConfiguration.token_endpoint}?grant_type=authorization_code&code=${authorizationCode}&redirect_uri=http://localhost:4000/`,
                        '',
                        null, {
                            "Authorization": "Basic " + applicationBase64Token(application2)
                        }
                    ).expect(400);

                    expect(badGrantResponse.body.error).toEqual("invalid_grant");
                    expect(badGrantResponse.body.error_description).toEqual("Missing parameter: code_verifier");

                    await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect);
                });

                it("must have invalid code_verifier PKCE - " + codeChallengeMethod, async () => {
                    const clientId = application2.settings.oauth.clientId;
                    const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/&state=1234-5678-9876` +
                        `&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=${codeChallengeMethod}`;

                    // Initiate the Login Flow
                    const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);

                    expect(authResponse.headers['location']).toBeDefined();
                    expect(authResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/login`);
                    expect(authResponse.headers['location']).toContain(`client_id=${clientId}`);

                    const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

                    // Authentication
                    const postLogin = await performFormPost(loginResult.action, '', {
                            "X-XSRF-TOKEN": loginResult.token,
                            "username": "user",
                            "password": "#CoMpL3X-P@SsW0Rd",
                            "client_id": clientId
                        }, {
                            'Cookie': loginResult.headers['set-cookie'],
                            'Content-type': 'application/x-www-form-urlencoded'
                        }
                    ).expect(302);

                    // Post authentication redirect
                    const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
                        'Cookie': postLogin.headers['set-cookie']
                    }).expect(302);

                    const redirectUri = postLoginRedirect.headers['location'];
                    const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
                    expect(redirectUri).toBeDefined();
                    expect(redirectUri).toContain("http://localhost:4000/?");
                    expect(redirectUri).toContain("state=");
                    expect(redirectUri).toMatch(codePattern);

                    const authorizationCode = redirectUri.match(codePattern)[1];

                    const badGrantResponse = await performPost(
                        `${openIdConfiguration.token_endpoint}?grant_type=authorization_code&code=${authorizationCode}`
                        + `&code_verifier=wrongcodeverifier&redirect_uri=http://localhost:4000/`,
                        '',
                        null, {
                            "Authorization": "Basic " + applicationBase64Token(application2)
                        }
                    ).expect(400);

                    expect(badGrantResponse.body.error).toEqual("invalid_grant");
                    expect(badGrantResponse.body.error_description).toEqual("Invalid parameter: code_verifier");

                    await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect);
                });
            }

            for (const challenge of [
                {"method": "S256", "code_verifier": "M25iVXpKU3puUjFaYWg3T1NDTDQtcW1ROUY5YXlwalNoc0hhakxifmZHag"},
                {"method": "plain", "code_verifier": "qjrzSW9gMiUgpUvqgEPE4_-8swvyCtfOVvg55o5S_es"}
            ]) {
                it("must have valid code_verifier PKCE - " + challenge.method, async () => {
                    const clientId = application2.settings.oauth.clientId;
                    const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/&state=1234-5678-9876` +
                        `&code_challenge=qjrzSW9gMiUgpUvqgEPE4_-8swvyCtfOVvg55o5S_es&code_challenge_method=${challenge.method}`;

                    // Initiate the Login Flow
                    const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);

                    expect(authResponse.headers['location']).toBeDefined();
                    expect(authResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/login`);
                    expect(authResponse.headers['location']).toContain(`client_id=${clientId}`);

                    const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

                    // Authentication
                    const postLogin = await performFormPost(loginResult.action, '', {
                            "X-XSRF-TOKEN": loginResult.token,
                            "username": "user",
                            "password": "#CoMpL3X-P@SsW0Rd",
                            "client_id": clientId
                        }, {
                            'Cookie': loginResult.headers['set-cookie'],
                            'Content-type': 'application/x-www-form-urlencoded'
                        }
                    ).expect(302);

                    // Post authentication redirect
                    const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
                        'Cookie': postLogin.headers['set-cookie']
                    }).expect(302);

                    const redirectUri = postLoginRedirect.headers['location'];
                    const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
                    expect(redirectUri).toBeDefined();
                    expect(redirectUri).toContain("http://localhost:4000/?");
                    expect(redirectUri).toContain("state=");
                    expect(redirectUri).toMatch(codePattern);

                    const authorizationCode = redirectUri.match(codePattern)[1];

                    const response = await performPost(
                        openIdConfiguration.token_endpoint,
                        `?grant_type=authorization_code&code=${authorizationCode}&code_verifier=${challenge.code_verifier}&redirect_uri=http://localhost:4000/`,
                        null, {
                            "Authorization": "Basic " + applicationBase64Token(application2)
                        }
                    ).expect(200);

                    assertGeneratedToken(response.body, "scope1")
                    await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect);
                });
            }

            describe("Authorize Request", () => {
                for (const actual of getParamsInvalidAuthorizeRequests()) {
                    it("must handle invalid Authorize requests - " + actual.title, async () => {
                        // Initiate the Login Flow
                        let authResponse;
                        if (actual.patchDomain) {
                            authResponse = await patchDomain(domain.id, accessToken, {
                                "oidc": {
                                    "redirectUriStrictMatching": true
                                }
                            })
                                .then(_ => new Promise((r) => setTimeout(r, 10000)))
                                .then(_ => performGet(openIdConfiguration.authorization_endpoint, actual.params).expect(302));
                        } else {
                            authResponse = await performGet(openIdConfiguration.authorization_endpoint, actual.params).expect(302)
                        }

                        expect(authResponse.header["location"]).toBeDefined();
                        expect(authResponse.header["location"]).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}${actual.uri}`);

                        if (actual.error && actual.error_description) {
                            const error = authResponse.header["location"].match(/error=([^&]+)&?/)[1];
                            const errorDescription = authResponse.header["location"].match(/error_description=([^&]+)&?/)[1];
                            expect(error).toEqual(actual.error);
                            expect(errorDescription).toEqual(actual.error_description);
                        }
                        if (actual.state) {
                            const state = authResponse.header["location"].match(/state=([^&]+)&?/)[1];
                            expect(state).toEqual(actual.state);
                        }
                    });
                }
            });
        });
    });
});


afterAll(async () => {
    if (domain && domain.id) {
        await deleteDomain(domain.id, accessToken);
    }
});

function assertGeneratedToken(data, scopes) {
    expect(data.access_token).toBeDefined();
    expect(data.token_type).toBeDefined();
    expect(data.token_type).toEqual("bearer");
    expect(data.expires_in).toBeDefined();
    if (scopes) {
        expect(data.scope).toBeDefined();
        expect(data.scope).toEqual(scopes);
    } else {
        expect(data.scope).not.toBeDefined();
    }
}

function getParamsInvalidAuthorizeRequests() {
    return [
        {
            "title": "Unsupported response type",
            "params": "?response_type=unknown_response_type",
            "uri": "/oauth/error",
            "error": "unsupported_response_type",
            "error_description": "Unsupported+response+type%253A+unknown_response_type",
        },
        {
            "title": "Duplicated query params",
            "params": "?response_type=unknown_response_type&response_type=unknown_response_type",
            "uri": "/oauth/error",
            "error": "invalid_request",
            "error_description": "Parameter+%255Bresponse_type%255D+is+included+more+than+once",
        },
        {
            "title": "Missing client id",
            "params": "?response_type=code",
            "uri": "/oauth/error",
            "error": "invalid_request",
            "error_description": "Missing+parameter%253A+client_id",
        },
        {
            "title": "Invalid client id",
            "params": "?response_type=code&client_id=wrong-client-id",
            "uri": "/oauth/error",
            "error": "invalid_request",
            "error_description": "No+client+found+for+client_id+wrong-client-id",
        },
        {
            "title": "Send an unknown redirect_uri and no redirect_uri defined",
            "params": "?response_type=code&client_id=clientId-test-1&redirect_uri=http://localhost:4000",
            "uri": `/login`
        },
        {
            "title": "Send a redirect_uri not configured in the client",
            "params": "?response_type=code&client_id=client-test-2&redirect_uri=http://my_bad_host:4000",
            "uri": `/oauth/error`,
            "error": "redirect_uri_mismatch",
            "error_description": "The+redirect_uri+MUST+match+the+registered+callback+URL+for+this+application",
        },
        {
            "title": "Send a bad redirect_uri strict matching",
            "patchDomain": true,
            "params": "?response_type=code&client_id=client-test-2&redirect_uri=http://my_bad_host:4000?extraParam=test",
            "uri": `/oauth/error`,
            "error": "redirect_uri_mismatch",
            "error_description": "The+redirect_uri+MUST+match+the+registered+callback+URL+for+this+application",
        },
        {
            "title": "Error with state parameters",
            "params": "?response_type=code&client_id=client-test-2&redirect_uri=http://my_bad_host:4000&state=xxx-yyy-zzz",
            "uri": `/oauth/error`,
            "error": "redirect_uri_mismatch",
            "error_description": "The+redirect_uri+MUST+match+the+registered+callback+URL+for+this+application",
            "state": "xxx-yyy-zzz"
        }
    ];
}
