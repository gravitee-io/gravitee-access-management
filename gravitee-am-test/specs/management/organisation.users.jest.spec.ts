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
import {afterAll, beforeAll, expect} from "@jest/globals";
import {
    createOrganisationUser, deleteOrganisationUser, getCurrentUser, getOrganisationUserPage, updateOrganisationUsername
} from "@management-commands/organisation-user-commands";

import {requestAccessToken, requestAdminAccessToken} from "@management-commands/token-management-commands";

global.fetch = fetch;

let accessToken;
let organisationUser;
const password = "SomeP@ssw0rd";
let organisationUserToken;

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
            username: "organization_username",
            password: password,
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
    it('it should get current user', async () => {
        const responseAccess = await requestAccessToken(organisationUser.username, password);
        organisationUserToken = responseAccess.body.access_token;

        const currentOrgUser = await getCurrentUser(organisationUserToken);
        expect(currentOrgUser.email).toEqual(organisationUser.email);
    });

    it('should change organisation username', async () => {
        const username = "my-new-username";
        organisationUser = await updateOrganisationUsername(accessToken, organisationUser.id, username);
        expect(organisationUser.username).toEqual(username);
    })

    it('should not be authorized due to username change', async () => {
        let isUserDisconnected = false;
        try {
            await getCurrentUser(organisationUserToken)
        } catch (e) {
            isUserDisconnected = true;
        }
        expect(isUserDisconnected).toBeTruthy();
    });

    it('it should get current user with new username', async () => {
        const responseAccess = await requestAccessToken(organisationUser.username, password);
        const newOrganisationUserToken = responseAccess.body.access_token;

        expect(newOrganisationUserToken).toBeDefined();
        expect(newOrganisationUserToken).not.toEqual(organisationUserToken);

    });
});

afterAll(async () => {
    if (organisationUser && organisationUser.id) {
        await deleteOrganisationUser(accessToken, organisationUser.id);
        const userPage = await getOrganisationUserPage(accessToken);
        expect(userPage.data.find(user => user.id === organisationUser.id)).toBeUndefined();
    }
});