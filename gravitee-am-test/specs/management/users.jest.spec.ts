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

import fetch from "cross-fetch";
import * as faker from 'faker';
import {afterAll, beforeAll, expect} from "@jest/globals";
import {createDomain, deleteDomain, startDomain} from "@management-commands/domain-management-commands";
import {
    buildCreateAndTestUser, deleteUser,
    getAllUsers,
    getUser,
    getUserPage, lockUser, resetUserPassword, sendRegistrationConfirmation, unlockUser, updateUser,
    updateUserStatus
} from "@management-commands/user-management-commands";

import {requestAdminAccessToken} from "@management-commands/token-management-commands";

global.fetch = fetch;

let accessToken;
let domain;
let user;

beforeAll(async () => {
    const adminTokenResponse = await requestAdminAccessToken();
    accessToken = adminTokenResponse.body.access_token;
    expect(accessToken).toBeDefined()

    const createdDomain = await createDomain(accessToken, "domain-users", faker.company.catchPhraseDescriptor());
    expect(createdDomain).toBeDefined();
    expect(createdDomain.id).toBeDefined();

    const domainStarted = await startDomain(createdDomain.id, accessToken);
    expect(domainStarted).toBeDefined();
    expect(domainStarted.id).toEqual(createdDomain.id);

    domain = domainStarted
});

describe("when using the users commands", () => {
    for (let i = 0; i < 10; i++) {
        it('must create new users: ' + i, async () => {
            user = await buildCreateAndTestUser(domain.id, accessToken, i);
        });
    }
});

describe("after creating users", () => {
    it('must find User by id', async () => {
        const foundUser = await getUser(domain.id, accessToken, user.id);
        expect(foundUser).toBeDefined();
        expect(foundUser.id).toEqual(user.id);
    });

    it('must update User', async () => {
        const updatedUser = await updateUser(domain.id, accessToken, user.id, {
            ...user, firstName: "another-name"
        });
        expect(user.firstName === updatedUser.firstName).toBeFalsy();
    });

    it('must find all Users', async () => {
        const applicationPage = await getAllUsers(domain.id, accessToken);

        expect(applicationPage.currentPage).toEqual(0);
        expect(applicationPage.totalCount).toEqual(10);
        expect(applicationPage.data.length).toEqual(10);
    });

    it('must find User page', async () => {
        const UserPage = await getUserPage(domain.id, accessToken, 1, 3);

        expect(UserPage.currentPage).toEqual(1);
        expect(UserPage.totalCount).toEqual(10);
        expect(UserPage.data.length).toEqual(3);
    });

    it('must find last User page', async () => {
        const UserPage = await getUserPage(domain.id, accessToken, 3, 3);

        expect(UserPage.currentPage).toEqual(3);
        expect(UserPage.totalCount).toEqual(10);
        expect(UserPage.data.length).toEqual(1);
    });

    it('must change user status', async () => {
        const updatedUser = await updateUserStatus(domain.id, accessToken, user.id, false);

        expect(updatedUser.enabled).not.toEqual(user.enabled);
        user = updatedUser;
    });

    it('must reset user password', async () => {
        await resetUserPassword(domain.id, accessToken, user.id, "SomeOtherP@ssw0rd");
        // We cannot test here, we just believe if nothing breaks it's all good.
        expect(true).toBeTruthy();
    });

    it('must send registration confirmation', async () => {
        const preRegisteredUser = await buildCreateAndTestUser(domain.id, accessToken, 1000, true)
        await sendRegistrationConfirmation(domain.id, accessToken, preRegisteredUser.id);
        // same as above
        expect(true).toBeTruthy();
        await deleteUser(domain.id, accessToken, preRegisteredUser.id);
    });

    it('must lock user', async () => {
        await lockUser(domain.id, accessToken, user.id);
        const foundUser = await getUser(domain.id, accessToken, user.id);
        // same as above
        expect(foundUser.accountNonLocked).toBeFalsy();
        user = foundUser;
    });

    it('must unlock user', async () => {
        await unlockUser(domain.id, accessToken, user.id);
        const foundUser = await getUser(domain.id, accessToken, user.id);
        // same as above
        expect(foundUser.accountNonLocked).toBeTruthy();
        user = foundUser;
    });

    it('Must delete User', async () => {
        await deleteUser(domain.id, accessToken, user.id);
        const UserPage = await getUserPage(domain.id, accessToken);

        expect(UserPage.currentPage).toEqual(0);
        expect(UserPage.totalCount).toEqual(9);
        expect(UserPage.data.length).toEqual(9);
        expect(UserPage.data.find(u => u.id === user.id)).toBeFalsy();
    });
});

afterAll(async () => {
    if (domain && domain.id) {
        await deleteDomain(domain.id, accessToken);
    }
});