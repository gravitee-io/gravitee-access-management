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

const request = require("supertest");

export const createGroup = (domainId, accessToken, group, status: number = 201) =>
    request(getGroupsUrl(domainId, null))
        .post('')
        .set('Authorization', 'Bearer ' + accessToken)
        .send(group)
        .expect(status);

export const getGroup = (domainId, accessToken, groupId, status: number = 200) =>
    request(getGroupsUrl(domainId, groupId))
        .get('')
        .set('Authorization', 'Bearer ' + accessToken)
        .expect(status);

export const getAllGroups = (domainId, accessToken, status: number = 200) =>
    getGroup(domainId, accessToken, null, status);

export const updateGroup = (domainId, accessToken, groupId, payload, status: number = 200) =>
    request(getGroupsUrl(domainId, groupId))
        .put('')
        .set('Authorization', 'Bearer ' + accessToken)
        .send(payload)
        .expect(status);

export const deleteGroup = (domainId, accessToken, groupId, status: number = 204) =>
    request(getGroupsUrl(domainId, groupId))
        .delete('')
        .set('Authorization', 'Bearer ' + accessToken)
        .expect(status);

export const addRolesToGroup = (domainId, accessToken, groupId, roles: Array<string>, status: number = 200) =>
    request(getDomainManagerUrl(domainId) + `/groups/${groupId}/roles`)
        .post('')
        .set('Authorization', 'Bearer ' + accessToken)
        .send(roles)
        .expect(status);

const getGroupsUrl = (domainId, groupId) => {
    const groupsPath = groupId ? `/groups/${groupId}` : '/groups/';
    return getDomainManagerUrl(domainId) + groupsPath;
}
