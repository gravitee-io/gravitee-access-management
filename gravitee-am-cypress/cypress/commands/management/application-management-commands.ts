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

export const createApplication = (domainId, accessToken, body, status: number = 201) => {
    return requestApplication(domainId, null, "POST", accessToken, body)
        .should(response => expect(response.status).to.eq(status));
};

export const getApplication = (domainId, accessToken, applicationId, status: number = 200) => {
    return requestApplication(domainId, applicationId, "GET", accessToken, null)
        .should(response => expect(response.status).to.eq(status));
};

export const getAllApplications = (domainId, accessToken, status: number = 200) => {
    return requestApplication(domainId, null, "GET", accessToken, null)
        .should(response => expect(response.status).to.eq(status));
};

export const updateApplication = (domainId, accessToken, payload, applicationId, status: number = 200) => {
    return requestApplication(domainId, applicationId, "PATCH", accessToken, payload)
        .should(response => expect(response.status).to.eq(status));
};

export const deleteApplication = (domainId, accessToken, applicationId, status: number = 204) => {
    return requestApplication(domainId, applicationId, "DELETE", accessToken, null)
        .should(response => expect(response.status).to.eq(status));
};

const requestApplication = (domainId, application, method, accessToken, body) => {
    return cy.request({
        url: getApplicationUrl(domainId, application),
        method: method,
        auth: {bearer: accessToken},
        body: body,
        failOnStatusCode: false
    });
}

const getApplicationUrl = (domainId, application) => {
    const appPath = application ? `/applications/${application}` : '/applications/';
    return getDomainManagerUrl(domainId) + appPath;
}
