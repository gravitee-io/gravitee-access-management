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

const request = require('supertest');

export const createUser = (domainId, accessToken, user: User, status: number = 201) =>
    request(getUsersUrl(domainId, null))
        .post('')
        .set('Authorization', 'Bearer ' + accessToken)
        .send(user)
        .expect(status);

export const getUser = (domainId, accessToken, userId: string, status: number = 200) =>
    request(getUsersUrl(domainId, userId))
        .get('')
        .set('Authorization', 'Bearer ' + accessToken)
        .expect(status);

export const getAllUsers = (domainId, accessToken, status: number = 200) =>
    getUser(domainId, accessToken, null, status);

export const updateUser = (domainId, accessToken, userId, payload, status: number = 200) =>
    request(getUsersUrl(domainId, userId))
        .put('')
        .set('Authorization', 'Bearer ' + accessToken)
        .send(payload)
        .expect(status);

export const deleteUser = (domainId, accessToken, userId, status: number = 204) =>
    request(getUsersUrl(domainId, userId))
        .delete('')
        .set('Authorization', 'Bearer ' + accessToken)
        .expect(status);

const getUsersUrl = (domainId, userId) => {
    const usersPath = userId ? `/users/${userId}` : '/users/';
    return getDomainManagerUrl(domainId) + usersPath;
}
