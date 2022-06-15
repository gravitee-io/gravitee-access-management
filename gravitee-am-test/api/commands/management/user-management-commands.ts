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
import {getUserApi} from "./service/utils";
import {expect} from "@jest/globals";

export const createUser = (domainId, accessToken, user) =>
    getUserApi(accessToken).createUser({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        user: user
    })

export const getUser = (domainId, accessToken, userId: string) =>
    getUserApi(accessToken).findUser({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        user: userId
    })

export const getAllUsers = (domainId, accessToken) =>
    getUserPage(domainId, accessToken)

export const getUserPage = (domainId, accessToken, page: number = null, size: number = null) => {
    const params = {
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
    };
    if (page !== null && size != null) {
        return getUserApi(accessToken).listUsers({...params, page: page, size: size});
    }
    return getUserApi(accessToken).listUsers(params);
}

export const updateUser = (domainId, accessToken, userId, payload) =>
    getUserApi(accessToken).updateUser({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        user: userId,
        user2: payload
    })

export const updateUserStatus = (domainId, accessToken, userId, status: boolean) =>
    getUserApi(accessToken).updateUserStatus({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        user: userId,
        status: {enabled: status}
    })

export const resetUserPassword = (domainId, accessToken, userId, password) =>
    getUserApi(accessToken).resetPassword({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        user: userId,
        password: { password: password }
    })

export const sendRegistrationConfirmation = (domainId, accessToken, userId) =>
    getUserApi(accessToken).sendRegistrationConfirmation({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        user: userId
    })

export const lockUser = (domainId, accessToken, userId) =>
    getUserApi(accessToken).lockUser({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        user: userId
    })

export const unlockUser = (domainId, accessToken, userId) =>
    getUserApi(accessToken).unlockUser({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        user: userId
    })

export const deleteUser = (domainId, accessToken, userId) =>
    getUserApi(accessToken).deleteUser({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        user: userId,
    })

export async function buildCreateAndTestUser(domainId, accessToken, i: number, preRegistration: boolean = false) {
    const firstName = "firstName" + i;
    const lastName = "lastName" + i;
    const payload = {
        firstName: firstName,
        lastName: lastName,
        email: `${firstName}.${lastName}@mail.com`,
        username: `${firstName}.${lastName}`,
        password: "SomeP@ssw0rd",
        preRegistration: preRegistration
    }

    const newUser = await createUser(domainId, accessToken, payload)
    expect(newUser).toBeDefined();
    expect(newUser.id).toBeDefined();
    expect(newUser.firstName).toEqual(payload.firstName);
    expect(newUser.lastName).toEqual(payload.lastName);
    expect(newUser.username).toEqual(payload.username);
    expect(newUser.email).toEqual(payload.email);
    expect(newUser.password).not.toBeDefined();
    return newUser;
}
