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
import {User} from "../../model/users";

export const createGroup = (domainId, accessToken, group, status: number = 201) => {
    return requestGroup(domainId, accessToken, "POST", null, group)
        .should(response => expect(response.status).to.eq(status));
};

export const getGroup = (domainId, accessToken, groupId, status: number = 200) => {
    return requestGroup(domainId, accessToken, "GET", groupId, null)
        .should(response => expect(response.status).to.eq(status));
};

export const getAllGroups = (domainId, accessToken, status: number = 200) => {
    return requestGroup(domainId, accessToken, "GET", null, null)
        .should(response => expect(response.status).to.eq(status));
};

export const updateGroup = (domainId, accessToken, groupId, payload, status: number = 200) => {
    return requestGroup(domainId, accessToken, "PUT", groupId, payload)
        .should(response => expect(response.status).to.eq(status));
};

export const addRolesToGroup = (domainId, accessToken, groupId, roles: Array<string>, status: number = 200) => {
    return cy.request({
        url: getDomainManagerUrl(domainId) + `/groups/${groupId}/roles`,
        method: "POST",
        auth: {bearer: accessToken},
        body: roles
    }).should(response => expect(response.status).to.eq(status));
};

const requestGroup = (domainId, accessToken, method, groupId: string, payload) => {
    return cy.request({
        url: getGroupsUrl(domainId, groupId),
        method: method,
        auth: {bearer: accessToken},
        body: payload
    });
}

const getGroupsUrl = (domainId, groupId) => {
    const groupsPath = groupId ? `/groups/${groupId}` : '/groups/';
    return getDomainManagerUrl(domainId) + groupsPath;
}