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
import {
    createAcceptAllDeviceNotifier,
    createDomain,
    requestDomain,
    startDomain
} from "../../../commands/management/domain-management-commands";
import {domainOidcCibaConfig} from "../../../fixtures/oidc";
import {User} from "../../../model/users";
import {requestAdminAccessToken} from "../../../commands/management/token-management-commands";
import {createUser} from "../../../commands/management/user-management-commands";
import {
    configureDomainForCiba,
    createCibaApplication,
    registerDomainWithNotifier,
    requestAuthentication
} from "./ciba-commands";

describe("An application is correctly configured to use CIBA", () => {
    let accessToken;
    let domain;
    let client;
    let auth_req_id;
    let cibaConfig;
    const user: User = {
        firstName: "Pat",
        lastName: "Test",
        email: "pat@test.com",
        username: "pat@test.com",
        password: "Password123",
        additionalInformation: {
            user_code: "1234"
        }
    };

    before(() => {
        requestAdminAccessToken()
            .then(response => accessToken = response.body.access_token)
            .then(() => createDomain(accessToken, "cibatest", "A test domain for CIBA"))
            .then(response => domain = response.body)
            .then(() => createAcceptAllDeviceNotifier(domain.id, accessToken))
            .then(response => cibaConfig = domainOidcCibaConfig(response.body.id))
            .then(() => configureDomainForCiba(domain.id, accessToken, "PATCH", cibaConfig))
            .then(() => startDomain(domain.id, accessToken))
            .then(() => createCibaApplication(domain.hrid))
            .then(response => client = response.body)
            .then(() => registerDomainWithNotifier(domain.id, domain.hrid, client.client_id, client.client_secret))
            .then(() => createUser(domain.id, accessToken, user));
    });

    after(() => {
        requestDomain(domain.id, accessToken, "DELETE", null)
            .should(response => expect(response.status).to.eq(204))
    })

    context("The application requests authentication", () => {
        it("AM responds with an auth_req_id", () => {
            let loginHint = user.username;
            let userCode = user.additionalInformation["user_code"];
            requestAuthentication(domain.hrid, client.client_id, client.client_secret, loginHint, userCode).should(response => {
                expect(response.status).to.eq(200);
                expect(response.body.auth_req_id).to.not.be.null;
                expect(response.body.expires_in).to.not.be.null;
                expect(response.body.interval).to.eq(cibaConfig.oidc.cibaSettings.tokenReqInterval);
            }).then(response => {
                auth_req_id = response.body.auth_req_id
            });
        })
    });

    context("The application requests an access token when the user has accepted", () => {
        it('should receive an access token', () => {
            cy.request({
                url: Cypress.env().gatewayUrl + `/${domain.hrid}/oauth/token`,
                method: 'POST',
                auth: {
                    user: client.client_id,
                    password: client.client_secret
                },
                form: true,
                body: {
                    auth_req_id: auth_req_id,
                    grant_type: "urn:openid:params:grant-type:ciba",
                }
            }).should(response => {
                expect(response.status).to.eq(200);
                expect(response.body.access_token).to.not.be.null;
                expect(response.body.id_token).to.not.be.null;
            });
        });
    });

    context("An incorrect user code is sent in the authentication request", () => {
        it("AM rejects the request with invalid_user_code", () => {
            let loginHint = user.username;
            let userCode = "wrong";
            requestAuthentication(domain.hrid, client.client_id, client.client_secret, loginHint, userCode).should(response => {
                expect(response.status).to.eq(400);
                expect(response.body).to.deep.eq({
                    error: "invalid_request",
                    error_description: "Invalid user_code"
                })
            });
        })
    });
})