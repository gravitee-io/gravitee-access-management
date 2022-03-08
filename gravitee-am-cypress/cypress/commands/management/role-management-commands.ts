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

export const createRole = (domainId, accessToken, role, status: number = 201) => {
    return requestRole(domainId, accessToken, "POST", null, role)
        .should(response => expect(response.status).to.eq(status));
};

export const getRole = (domainId, accessToken, roleId, status: number = 200) => {
    return requestRole(domainId, accessToken, "GET", roleId, null)
        .should(response => expect(response.status).to.eq(status));
};

export const getAllRoles = (domainId, accessToken, status: number = 200) => {
    return requestRole(domainId, accessToken, "GET", null, null)
        .should(response => expect(response.status).to.eq(status));
};

export const updateRole = (domainId, accessToken, roleId, payload, status: number = 200) => {
    return requestRole(domainId, accessToken, "PUT", roleId, payload)
        .should(response => expect(response.status).to.eq(status));
};

const requestRole = (domainId, accessToken, method, roleId, payload) => {
    return cy.request({
        url: getRolesUrl(domainId, roleId),
        method: method,
        auth: {bearer: accessToken},
        body: payload
    });
}

const getRolesUrl = (domainId, roleId) => {
    const rolesPath = roleId ? `/roles/${roleId}` : '/roles/';
    return getDomainManagerUrl(domainId) + rolesPath;
}