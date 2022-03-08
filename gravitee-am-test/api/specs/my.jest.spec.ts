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
import {afterAll, beforeAll, expect} from '@jest/globals';
import {requestAdminAccessToken} from "../commands/management/token-management-commands";
import {createDomain, deleteDomain} from "../commands/management/domain-management-commands";
import {ApplicationType, NewApplication} from "../model/applications";
import {
    createApplication, deleteApplication,
    getAllApplications,
    getApplication, updateApplication
} from "../commands/management/application-management-commands";
import {User} from "../model/users";
import {Group} from "../model/groups";
import {Role} from "../model/roles";
import {
    createUser,
    deleteUser,
    getAllUsers,
    getUser,
    updateUser
} from "../commands/management/user-management-commands";
import {createRole, deleteRole, getAllRoles, getRole, updateRole} from "../commands/management/role-management-commands";
import {
    addRolesToGroup,
    createGroup,
    deleteGroup,
    getAllGroups,
    getGroup,
    updateGroup
} from "../commands/management/group-management-commands";

let accessToken;
let domainId;
const notFound = 404;
const apps = [];

beforeAll(async () => {
    let response = await requestAdminAccessToken();
    accessToken = response.body.access_token;
    response = await createDomain(accessToken, "testdom", "Domain created by tests");
    domainId = response.body.id;
});

describe("when using the application commands", () => {
    it('CRUD actions can be performed', async () => {
        const app: NewApplication = {
            "name": "my-client",
            "type": ApplicationType.SERVICE,
            "metadata": {"key": "value"}
        };
        let response = await createApplication(domainId, accessToken, app);
        let myapp = response.body;
        apps.push(myapp.id);
        expect(myapp.name).toEqual(app.name);
        response = await getApplication(domainId, accessToken, myapp.id);
        expect(response.body.id).toEqual(myapp.id);
        response = await getAllApplications(domainId, accessToken);
        expect(response.body.data.length).toEqual(1);
        response = await updateApplication(domainId, accessToken, {name: "my-app"}, myapp.id);
        expect(response.body.name).toEqual('my-app');
        await deleteApplication(domainId, accessToken, myapp.id);
        await getApplication(domainId, accessToken, myapp.id, notFound);
    });
});

describe("when using the user commands", () => {
    it('CRUD actions can be performed', async () => {
        class StubUser implements User {
            firstName = "Pat";
            lastName = "Test";
            email = "pat@test.com";
            username = "pat@test.com";
            password = "Password123";
        }

        const user = new StubUser();
        let myUser;
        let myRole: Role = {name: "myrole"};
        let response = await createUser(domainId, accessToken, user);
        expect(response.body.username).toEqual(user.username);
        expect(response.body.id).toBeDefined();
        myUser = response.body;
        response = await getUser(domainId, accessToken, myUser.id);
        expect(response.body.id).toEqual(myUser.id);
        await updateUser(domainId, accessToken, myUser.id, {firstName: "bob", email: myUser.email});
        response = await getAllUsers(domainId, accessToken);
        expect(response.body.data.length).toEqual(1);
        expect(response.body.data[0].firstName).toEqual("bob");
        response = await createRole(domainId, accessToken, myRole);
        myRole = response.body;
        response = await getRole(domainId, accessToken, myRole.id);
        expect(response.body.id).toEqual(myRole.id);
        response = await getAllRoles(domainId, accessToken);
        expect(response.body.data.length).toEqual(1);
        let description = "a description";
        let permissions = ["read"];
        await updateRole(domainId, accessToken, myRole.id, {
            name: "new name",
            description: description,
            permissions: permissions
        });
        response = await getRole(domainId, accessToken, myRole.id);
        expect(response.body.permissions).toEqual(permissions);
        let myGroup: Group = {name: "mygroup"};
        response = await createGroup(domainId, accessToken, myGroup);
        myGroup = response.body;
        response = await getGroup(domainId, accessToken, myGroup.id);
        expect(response.body.id).toEqual(myGroup.id);
        response = await getAllGroups(domainId, accessToken);
        expect(response.body.data.length).toBe(1);

        response = await updateGroup(domainId, accessToken, myGroup.id, {
                    name: myGroup.name,
                    members: [myUser.id]
                });
        expect(response.body.members).toContain(myUser.id);

        response = await addRolesToGroup(domainId, accessToken, myGroup.id, [myRole.id]);
        expect(response.body.roles).toContain(myRole.id);

        await deleteGroup(domainId, accessToken, myGroup.id);
        await getGroup(domainId, accessToken, myGroup.id, notFound);
        await deleteRole(domainId, accessToken, myRole.id);
        await getRole(domainId, accessToken, myRole.id, notFound);
        await deleteUser(domainId, accessToken, myUser.id);
        await getUser(domainId, accessToken, myUser.id, notFound);
    });
});

afterAll(async () => {
    if (domainId) {
        await deleteDomain(domainId, accessToken);
    }
});
