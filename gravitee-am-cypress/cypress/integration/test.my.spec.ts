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
import {createDomain, deleteDomain} from "../commands/management/domain-management-commands";
import {requestAdminAccessToken} from "../commands/management/token-management-commands";
import "../commands/management/application-management-commands";
import {
    createApplication,
    deleteApplication,
    getAllApplications,
    getApplication,
    updateApplication
} from "../commands/management/application-management-commands";
import {
    createUser,
    deleteUser,
    getAllUsers,
    getUser,
    updateUser
} from "../commands/management/user-management-commands";
import {createRole, getAllRoles, getRole, updateRole} from "../commands/management/role-management-commands";
import {User} from "../model/users";
import {ApplicationType, NewApplication} from "../model/applications";
import {Group} from "../model/groups";
import {Role} from "../model/roles";
import {
    addRolesToGroup,
    createGroup,
    getAllGroups,
    getGroup,
    updateGroup
} from "../commands/management/group-management-commands";

describe("Demonstration of how to use the utility commands", () => {
    let accessToken;
    let domainId;
    const notFound = 404;

    before(() => {
        requestAdminAccessToken()
            .then(response => accessToken = response.body.access_token)
            .then(() => createDomain(accessToken, "testdom", "Another test domain"))
            .then(response => domainId = response.body.id);
    });

    context("when using the application commands", () => {
        it('CRUD actions can be performed', () => {
            const app: NewApplication = {
                "name": "my-client",
                "type": ApplicationType.SERVICE,
                "metadata": {"key": "value"}
            };
            let myapp;
            createApplication(domainId, accessToken, app)
                .then(res => {
                    expect(res).to.not.be.null;
                    expect(res.body.name).to.eq(app.name);
                    myapp = res.body;
                })
                .then(() => {
                    getApplication(domainId, accessToken, myapp.id)
                        .then(res => expect(res.body.id).to.eq(myapp.id))
                })
                .then(() => {
                    createApplication(domainId, accessToken, app);
                    createApplication(domainId, accessToken, app);
                    createApplication(domainId, accessToken, app);
                    createApplication(domainId, accessToken, app);
                })
                .then(() => {
                    getAllApplications(domainId, accessToken)
                        .then(res => expect(res.body.data.length).to.eq(5));
                    updateApplication(domainId, accessToken, {name: "my_app"}, myapp.id)
                        .then(res => expect(res.body.name).to.eq("my_app"));
                })
                .then(() => {
                    deleteApplication(domainId, accessToken, myapp.id).then(() => {
                        getApplication(domainId, accessToken, myapp.id, notFound);
                    });
                });
        });
    });

    context("when using the user commands", () => {
        it('CRUD actions can be performed', () => {
            class StubUser implements User {
                firstName = "Pat";
                lastName = "Test";
                email = "pat@test.com";
                username = "pat@test.com";
                password = "Password123";
            }

            const user = new StubUser();
            let myUser;
            const expectedUserCount = 6;
            let myGroup: Group = {name: "mygroup"};
            let myRole: Role = {name: "myrole"};
            createUser(domainId, accessToken, user).then(res => {
                expect(res.body.firstName).to.eq(user.firstName);
                expect(res.body.id).to.not.be.null;
                myUser = res.body;
            }).then(() => {
                getUser(domainId, accessToken, myUser.id).then(res => expect(res.body.id).to.eq(myUser.id));

                createRole(domainId, accessToken, myRole).then(res => {
                    myRole = res.body;
                }).then(() => {
                    getRole(domainId, accessToken, myRole.id).then(res => {
                        expect(res.body.name).to.eq(myRole.name);
                    });
                    getAllRoles(domainId, accessToken).then(res => {
                        expect(res.body.data.length).to.eq(1);
                        expect(res.body.data[0].name).to.eq(myRole.name);
                    });
                    let description = "a description";
                    let permissions = ["read"];
                    updateRole(domainId, accessToken, myRole.id, {
                        name: "new name",
                        description: description,
                        permissions: permissions
                    }).then(res => {
                        expect(res.body.description).to.eq(description);
                    });
                });


                createGroup(domainId, accessToken, myGroup).then(res => {
                    myGroup = res.body;
                }).then(() => {
                    getGroup(domainId, accessToken, myGroup.id).then(res => {
                        expect(res.body.name).to.eq(myGroup.name);
                    });
                    getAllGroups(domainId, accessToken).then(res => {
                        expect(res.body.data.length).to.eq(1);
                        expect(res.body.data[0].name).to.eq(myGroup.name);
                    });
                    updateGroup(domainId, accessToken, myGroup.id, {
                        name: myGroup.name,
                        members: [myUser.id]
                    }).then(() => {
                        getGroup(domainId, accessToken, myGroup.id).then(res => {
                            expect(res.body.members).to.contain(myUser.id);
                        });
                    });
                    addRolesToGroup(domainId, accessToken, myGroup.id, [myRole.id]).then(res => {
                        expect(res.body.roles).to.contain(myRole.id);
                    });
                });

            }).then(() => {
                for (let i = 1; i < expectedUserCount; i++) {
                    let anotherUser = new StubUser();
                    anotherUser.username += i;
                    createUser(domainId, accessToken, anotherUser);
                }
            }).then(() => {
                getAllUsers(domainId, accessToken)
                    .then(res => expect(res.body.data.length).to.eq(expectedUserCount));
            }).then(() => {
                let newName = "Patrick";
                updateUser(domainId, accessToken, myUser.id, {
                    firstName: newName,
                    email: myUser.email,
                    lastName: myUser.lastName
                }).then(res => expect(res.body.firstName).to.eq(newName));
            }).then(() => {
                deleteUser(domainId, accessToken, myUser.id)
                    .then(() => {
                        getUser(domainId, accessToken, myUser.id, notFound);
                    });
            });


        });
    });

    after(() => {
        if (domainId) {
            deleteDomain(domainId, accessToken)
        }
    });
})