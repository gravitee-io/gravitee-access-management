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

export const createUser = (domainId, accessToken, user: User, status: number = 201) => {
    return requestUser(domainId, "POST", accessToken, null, user)
        .should(response => expect(response.status).to.eq(status));
};

export const getUser = (domainId, accessToken, userId: string, status: number = 200) => {
    return requestUser(domainId, "GET", accessToken, userId, null)
        .should(response => expect(response.status).to.eq(status));
};

export const getAllUsers = (domainId, accessToken, status: number = 200) => {
    return requestUser(domainId, "GET", accessToken, null, null)
        .should(response => expect(response.status).to.eq(status));
};

export const updateUser = (domainId, accessToken, userId, payload, status: number = 200) => {
    return requestUser(domainId, "PUT", accessToken, userId, payload)
        .should(response => expect(response.status).to.eq(status));
};

export const deleteUser = (domainId, accessToken, userId, status: number = 204) => {
    return requestUser(domainId, "DELETE", accessToken, userId, null)
        .should(response => expect(response.status).to.eq(status));
};

const requestUser = (domainId, method, accessToken, userId: string, payload: User) => {
    return cy.request({
        url: getUsersUrl(domainId, userId),
        method: method,
        auth: {bearer: accessToken},
        body: payload,
        failOnStatusCode: false
    });
}

const getUsersUrl = (domainId, userId) => {
    const usersPath = userId ? `/users/${userId}` : '/users/';
    return getDomainManagerUrl(domainId) + usersPath;
}