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

const request = require('supertest');

export const createRole = (domainId, accessToken, role, status: number = 201) =>
    request(getRolesUrl(domainId, null))
        .post('')
        .set('Authorization', 'Bearer ' + accessToken)
        .send(role)
        .expect(status);

export const getRole = (domainId, accessToken, roleId, status: number = 200) =>
    request(getRolesUrl(domainId, roleId))
        .get('')
        .set('Authorization', 'Bearer ' + accessToken)
        .expect(status);

export const getAllRoles = (domainId, accessToken, status: number = 200) =>
    getRole(domainId, accessToken, null, status);

export const updateRole = (domainId, accessToken, roleId, payload, status: number = 200) =>
    request(getRolesUrl(domainId, roleId))
        .put('')
        .set('Authorization', 'Bearer ' + accessToken)
        .send(payload)
        .expect(status);

export const deleteRole = (domainId, accessToken, roleId, status: number = 204) =>
    request(getRolesUrl(domainId, roleId))
        .delete('')
        .set('Authorization', 'Bearer ' + accessToken)
        .expect(status);

const getRolesUrl = (domainId, roleId) => {
    const rolesPath = roleId ? `/roles/${roleId}` : '/roles/';
    return getDomainManagerUrl(domainId) + rolesPath;
}
