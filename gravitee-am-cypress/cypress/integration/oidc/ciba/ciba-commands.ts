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
import {requestDomain} from "../../../commands/management/domain-management-commands";
import {oidcApplication} from "../../../fixtures/oidc";

export const configureDomainForCiba = (domain, accessToken, method, body) =>
    requestDomain(domain, accessToken, "PATCH", body)
        .should(response => expect(response.status).to.eq(200));

export const createCibaApplication = (domainHrid) => cy.request({
    url: Cypress.env().gatewayUrl + `/${domainHrid}/oidc/register`,
    method: 'POST',
    retryOnStatusCodeFailure: true,
    body: oidcApplication
}).should(response => expect(response.status).to.eq(201));

export const registerDomainWithNotifier = (domain, domainHrid, clientId, clientSecret) => cy.request({
    url: Cypress.env().cibaDelegateNotifierUrl + "/domains",
    method: 'POST',
    body: {
        domainId: domain,
        domainCallback: Cypress.env().gatewayUrl + `/${domainHrid}/oidc/ciba/authenticate/callback`,
        clientId: clientId,
        clientSecret: clientSecret
    }
}).should(response => expect(response.status).to.eq(200));

export const requestAuthentication = (domainHrid, clientId, clientSecret, loginHint, userCode) => cy.request({
    url: Cypress.env().gatewayUrl + `/${domainHrid}/oidc/ciba/authenticate`,
    method: 'POST',
    auth: {
        user: clientId,
        password: clientSecret
    },
    form: true,
    body: {
        scope: "openid",
        login_hint: loginHint,
        user_code: userCode
    },
    failOnStatusCode: false
});