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
    getUserPage, lockUser, resetUserPassword, sendRegistrationConfirmation, unlockUser, updateUser, updateUsername,
    updateUserStatus
} from "@management-commands/user-management-commands";
import {
    createOrganisationUser, deleteOrganisationUser,
    getOrganisationUserPage,
    updateOrganisationUsername
} from "@management-commands/organisation-user-commands";

import {requestAdminAccessToken} from "@management-commands/token-management-commands";
import {ResponseError} from "../../api/management/runtime";

global.fetch = fetch;

let accessToken;
let domain;
let organisationUser;

beforeAll(async () => {
    const adminTokenResponse = await requestAdminAccessToken();
    accessToken = adminTokenResponse.body.access_token;
    expect(accessToken).toBeDefined()
});

describe("when using the users commands", () => {
    it('should create organisation user', async () => {
        const firstName = "orgUserFirstName";
        const lastName = "orgUserLastName";
        const payload = {
            firstName: firstName,
            lastName: lastName,
            email: `${firstName}.${lastName}@mail.com`,
            username: "orgUsername",
            password: "SomeP@ssw0rd",
            preRegistration: false
        };
        organisationUser = await createOrganisationUser(accessToken, payload);
        expect(organisationUser.id).toBeDefined();
        expect(organisationUser.firstName).toEqual(payload.firstName);
        expect(organisationUser.lastName).toEqual(payload.lastName);
        expect(organisationUser.username).toEqual(payload.username);
        expect(organisationUser.email).toEqual(payload.email);
    });
});

describe("when managing users at organisation level", () => {
    it('should change organisation username', async () => {
        const username = "my-new-username";
        const updatedUser = await updateOrganisationUsername(accessToken, organisationUser.id, username);
        expect(updatedUser.username).toEqual(username);
    });
});

afterAll(async () => {
    if (organisationUser && organisationUser.id) {
        await deleteOrganisationUser(accessToken, organisationUser.id);
        const userPage = await getOrganisationUserPage(accessToken);
        expect(userPage.data.find(user => user.id === organisationUser.id)).toBeUndefined();
    }
});