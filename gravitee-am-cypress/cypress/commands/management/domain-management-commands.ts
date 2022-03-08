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
import {getDomainManagerUrl} from "./utils";

export const createDomain = (accessToken, name, description) => {
    return requestDomain(null, accessToken, "POST", {
        name: name,
        description: description
    }).should(response => expect(response.status).to.eq(201));
};

export const startDomain = (domainId, accessToken) => {
    return requestDomain(domainId, accessToken, "PATCH", {
        enabled: true
    }).should(response => expect(response.status).to.eq(200));
};

export const deleteDomain = (domainId, accessToken) => {
    return requestDomain(domainId, accessToken, "DELETE", null)
        .should(response => expect(response.status).to.eq(204));
};

export const getDomain = (domainId, accessToken) => {
    return requestDomain(domainId, accessToken, "GET", null)
        .should(response => expect(response.status).to.eq(200));
};

export const patchDomain = (domainId, accessToken, body) => {
    return requestDomain(domainId, accessToken, "PATCH", body)
        .should(response => expect(response.status).to.eq(200));
};

export const requestDomain = (domainId, accessToken, method, body) => {
    return cy.request({
        url: getDomainManagerUrl(domainId),
        method: method,
        auth: {bearer: accessToken},
        body: body
    });
};

export const createAcceptAllDeviceNotifier = (domainId, accessToken) => {
    return cy.request({
        url: getDomainManagerUrl(domainId) + "/auth-device-notifiers",
        method: "POST",
        auth: {bearer: accessToken},
        body: {
            type: "http-am-authdevice-notifier",
            configuration: "{\"endpoint\":\"http://localhost:8080/ciba/notify/accept-all\",\"headerName\":\"Authorization\",\"connectTimeout\":5000,\"idleTimeout\":10000,\"maxPoolSize\":10}",
            name: "Always OK notifier"
        }
    }).should(response => expect(response.status).to.eq(201));
};
