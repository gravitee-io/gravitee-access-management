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
import {afterAll, beforeAll, expect, jest} from "@jest/globals";
import {requestAdminAccessToken} from "@management-commands/token-management-commands";
import {createDomain, deleteDomain, startDomain} from "@management-commands/domain-management-commands";
import {createIdp} from "@management-commands/idp-management-commands";
import {createUser, deleteUser, updateUsername} from "@management-commands/user-management-commands";
import {createApplication, updateApplication} from "@management-commands/application-management-commands";
import {
    extractXsrfTokenAndActionResponse,
    getWellKnownOpenIdConfiguration,
    logoutUser,
    performFormPost,
    performGet
} from "@gateway-commands/oauth-oidc-commands";

global.fetch = fetch;

const cheerio = require('cheerio');
const jdbc = process.env.GRAVITEE_MANAGEMENT_TYPE;

let accessToken;
let domain;
let customIdp;
let multiUserLoginApp;
let openIdConfiguration;

jest.setTimeout(200000);

beforeAll(async () => {
    const adminTokenResponse = await requestAdminAccessToken();
    accessToken = adminTokenResponse.body.access_token;
    domain = await createDomain(accessToken, "login-flow-domain", "test user login")
        .then(domain => startDomain(domain.id, accessToken));

    customIdp = jdbc === 'jdbc' ? await createJdbcIdp(domain.id, accessToken) : await createMongoIdp(domain.id, accessToken);
    multiUserLoginApp = await createTestApp("multi-user-login-app", customIdp.id, domain, accessToken);

    await new Promise((r) => setTimeout(r, 10000));

    const result = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
    openIdConfiguration = result.body;
});

describe('multiple user', () => {
    const contractValue = "1234";
    let user1;
    const user1Password = "Zxc123!!";
    let user2;
    const commonPassword = "Asd123!!";
    const commonEmail = "common@test.com"
    let user3;  //user3 has same password as user2
    let user4;
    const user4Password = "Qwe123!!"
    let user5;
    let user6;
    const secondCommonPassword = "Phd123!!";
    const secondCommonEmail = "second.common@test.com"

    beforeAll(async () => {
        user1 = await createUser(domain.id, accessToken, {
            "firstName": "john",
            "lastName": "doe",
            "email": "john.doe@test.com",
            "username": "john.doe",
            "password": user1Password,
            "client": multiUserLoginApp.id,
            "source": customIdp.id,
            "additionalInformation": {
                "contract": contractValue
            },
            "preRegistration": false
        });

        expect(user1).toBeDefined();

        user2 = await createUser(domain.id, accessToken, {
            "firstName": "jensen",
            "lastName": "barbara",
            "username": "jensen.barbara",
            "email": "jensen.barbara@test.com",
            "password": commonPassword,
            "client": multiUserLoginApp.id,
            "source": customIdp.id,
            "additionalInformation": {
                "contract": contractValue
            },
            "preRegistration": false
        });

        expect(user2).toBeDefined();

        user3 = await createUser(domain.id, accessToken, {
            "firstName": "flip",
            "lastName": "flop",
            "username": "flip.flop",
            "email": commonEmail,
            "password": commonPassword,
            "client": multiUserLoginApp.id,
            "source": customIdp.id,
            "additionalInformation": {
                "contract": contractValue
            },
            "preRegistration": false
        });

        expect(user3).toBeDefined();

        user4 = await createUser(domain.id, accessToken, {
            "firstName": "some",
            "lastName": "user",
            "username": "some.user",
            "email": commonEmail,
            "password": user4Password,
            "client": multiUserLoginApp.id,
            "source": customIdp.id,
            "additionalInformation": {
                "contract": contractValue
            },
            "preRegistration": false
        });

        expect(user4).toBeDefined();

        user5 = await createUser(domain.id, accessToken, {
            "firstName": "alan",
            "lastName": "bull",
            "username": "user.five",
            "email": secondCommonEmail,
            "password": secondCommonPassword,
            "client": multiUserLoginApp.id,
            "source": customIdp.id,
            "additionalInformation": {
                "contract": contractValue
            },
            "preRegistration": false
        });

        expect(user5).toBeDefined();

        user6 = await createUser(domain.id, accessToken, {
            "firstName": "james",
            "lastName": "hen",
            "username": "user.six",
            "email": secondCommonEmail,
            "password": secondCommonPassword,
            "client": multiUserLoginApp.id,
            "source": customIdp.id,
            "additionalInformation": {
                "contract": contractValue
            },
            "preRegistration": false
        });

        expect(user6).toBeDefined();
    });

    it("all users should be able to login using username and password", async () => {
        const clientId = multiUserLoginApp.settings.oauth.clientId;
        const user1TokenResponse = await loginUserNameAndPassword(clientId, user1, user1Password);
        expect(user1TokenResponse.headers['location']).toContain('callback?code=');
        await logoutUser(openIdConfiguration.end_session_endpoint, user1TokenResponse);

        const user2TokenResponse = await loginUserNameAndPassword(clientId, user2, commonPassword);
        expect(user2TokenResponse.headers['location']).toContain('callback?code=');
        await logoutUser(openIdConfiguration.end_session_endpoint, user2TokenResponse);

        //user3 has same password as user2
        const user3TokenResponse = await loginUserNameAndPassword(clientId, user3, commonPassword);
        expect(user3TokenResponse.headers['location']).toContain('callback?code=');
        await logoutUser(openIdConfiguration.end_session_endpoint, user3TokenResponse);

        const user4TokenResponse = await loginUserNameAndPassword(clientId, user4, user4Password);
        expect(user4TokenResponse.headers['location']).toContain('callback?code=');
        await logoutUser(openIdConfiguration.end_session_endpoint, user4TokenResponse);
    });

    if (jdbc === 'jdbc') {
        console.log("executing jdbc specific test")
        it("jdbc: both users should be able to login using additional information (email) and password", async () => {
            const clientId = multiUserLoginApp.settings.oauth.clientId;
            //log in user3
            const user3TokenResponse = await loginAdditionalInfoAndPassword(clientId, commonEmail, commonPassword);
            expect(user3TokenResponse.headers['location']).toContain('callback?code=');
            await logoutUser(openIdConfiguration.end_session_endpoint, user3TokenResponse);

            //log in user4
            const user4TokenResponse = await loginAdditionalInfoAndPassword(clientId, commonEmail, user4Password);
            await logoutUser(openIdConfiguration.end_session_endpoint, user4TokenResponse);
        });

        it("jdbc: should throw exception when more than one users have same password for login with additional information", async () => {
            const clientId = multiUserLoginApp.settings.oauth.clientId;
            const failedLoginResponse = await loginAdditionalInfoAndPassword(clientId, secondCommonEmail, secondCommonPassword);
            expect(failedLoginResponse.headers['location']).toContain(`error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+user`);
        });
    } else {
        console.log("executing mongodb specific test")
        it("mongo: both users should be able to login using additional information (contract) and password", async () => {
            const clientId = multiUserLoginApp.settings.oauth.clientId;

            //log in user1
            const user1TokenResponse = await loginAdditionalInfoAndPassword(clientId, contractValue, user1Password);
            expect(user1TokenResponse.headers['location']).toContain('callback?code=');
            await logoutUser(openIdConfiguration.end_session_endpoint, user1TokenResponse);

            //log in user4
            const user4TokenResponse = await loginAdditionalInfoAndPassword(clientId, contractValue, user4Password);
            await logoutUser(openIdConfiguration.end_session_endpoint, user4TokenResponse);
        });

        it("mongo: should throw exception when more than one users have same password for login with additional information", async () => {
            const clientId = multiUserLoginApp.settings.oauth.clientId;
            const failedLoginResponse = await loginAdditionalInfoAndPassword(clientId, contractValue, commonPassword);
            expect(failedLoginResponse.headers['location']).toContain(`error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+user`);
        });
    }

    it("user should have their username changed and have their session/token canceled", async () => {
        const clientId = multiUserLoginApp.settings.oauth.clientId;
        let user1TokenResponse = await loginUserNameAndPassword(clientId, user1, user1Password);
        expect(user1TokenResponse.headers['location']).toContain('callback?code=');
        await logoutUser(openIdConfiguration.end_session_endpoint, user1TokenResponse);

        user1 = await updateUsername(domain.id, accessToken, user1.id, user1.username + "-changed");
        const params = `?response_type=code&client_id=${clientId}&redirect_uri=https://auth-nightly.gravitee.io/myApp/callback`;

        const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);
        const loginLocation = authResponse.headers['location'];
        expect(loginLocation).not.toContain(`callback?code=`);
        await logoutUser(openIdConfiguration.end_session_endpoint, user1TokenResponse);

        user1TokenResponse = await loginUserNameAndPassword(clientId, user1, user1Password);
        expect(user1TokenResponse.headers['location']).toContain('callback?code=');
        await logoutUser(openIdConfiguration.end_session_endpoint, user1TokenResponse);
    });

    it("should throw exception user name and wrong password", async () => {
        const wrongPassword = "WrongPassword";
        const clientId = multiUserLoginApp.settings.oauth.clientId;

        const failedLoginResponse1 = await loginUserNameAndPassword(clientId, user1, wrongPassword);
        expect(failedLoginResponse1.headers['location']).toContain(`error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+user`);

        const failedLoginResponse2 = await loginUserNameAndPassword(clientId, user2, wrongPassword);
        expect(failedLoginResponse2.headers['location']).toContain(`error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+user`);

        const failedLoginResponse3 = await loginUserNameAndPassword(clientId, user3, wrongPassword);
        expect(failedLoginResponse3.headers['location']).toContain(`error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+us`);
    });

    afterAll(async () => {
        await deleteUser(domain.id, accessToken, user1.id);
        await deleteUser(domain.id, accessToken, user2.id);
        await deleteUser(domain.id, accessToken, user3.id);
        await deleteUser(domain.id, accessToken, user4.id);
        await deleteUser(domain.id, accessToken, user5.id);
        await deleteUser(domain.id, accessToken, user6.id);
    });
});

afterAll(async () => {
    if (domain.id) {
        await deleteDomain(domain.id, accessToken);
    }
});

const createTestApp = async (name, idpId, domain, accessToken) => {
    const application = await createApplication(domain.id, accessToken, {
        "name": name,
        "type": "WEB",
        "clientId": `${name}-id`
    }).then(app => updateApplication(domain.id, accessToken, {
        "settings": {
            "oauth": {
                "redirectUris": ["https://auth-nightly.gravitee.io/myApp/callback"],
                "scopeSettings": [{"scope": "openid", "defaultScope": true}, {
                    "scope": "openid",
                    "defaultScope": true
                }]
            }
        },
        "identityProviders": [
            {"identity": idpId, "priority": 0}
        ]
    }, app.id));

    expect(application).toBeDefined();
    expect(application.settings.oauth.clientId).toBeDefined();

    return application;
}

const initiateLoginFlow = async (clientId, openIdConfiguration, domain) => {
    const params = `?response_type=code&client_id=${clientId}&redirect_uri=https://auth-nightly.gravitee.io/myApp/callback`;

    const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);
    const loginLocation = authResponse.headers['location'];

    expect(loginLocation).toBeDefined();
    expect(loginLocation).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/login`);
    expect(loginLocation).toContain(`client_id=${clientId}`);

    return authResponse;
}

const login = async (authResponse, userName, clientId, password = "SomeP@ssw0rd") => {
    const loginResult = await extractXsrfTokenAndActionResponse(authResponse);
    return await performFormPost(loginResult.action, '', {
            "X-XSRF-TOKEN": loginResult.token,
            "username": userName,
            "password": password,
            "client_id": clientId
        }, {
            'Cookie': loginResult.headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded'
        }
    ).expect(302);
}

const postLoginAuthentication = async (postLogin) => {
    return await performGet(postLogin.headers['location'], '', {
        'Cookie': postLogin.headers['set-cookie']
    }).expect(302);
}

const postConsent = async (consent) => {
    const dom = cheerio.load(consent.text);
    const xsrfToken = dom("[name=X-XSRF-TOKEN]").val();
    return await performFormPost(consent.request.url, '', {
            "X-XSRF-TOKEN": xsrfToken,
            "scope.openid": true,
            "user_oauth_approval": true
        }, {
            'Cookie': consent.headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded'
        }
    ).expect(302);
}

const loginUserNameAndPassword = async (clientId, user, userPassword) => {
    return await loginUser(clientId, user.username, userPassword);
}

const loginAdditionalInfoAndPassword = async (clientId, additionalInfo, userPassword) => {
    return await loginUser(clientId, additionalInfo, userPassword);
}

const loginUser = async (clientId, nameOrAdditionalInfo, userPassword) => {
    const authResponse = await initiateLoginFlow(clientId, openIdConfiguration, domain);
    const postLogin = await login(authResponse, nameOrAdditionalInfo, clientId, userPassword);
    //log in failed with error
    if (postLogin.headers['location'].includes("error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+user")) {
        return postLogin;
    }
    const authorize = await postLoginAuthentication(postLogin);
    expect(authorize.headers['location']).toBeDefined();

    //log in for the very first time
    if (authorize.headers['location'].includes(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/consent`)) {
        const consent = await performGet(authorize.headers['location'], '', {
            'Cookie': authorize.headers['set-cookie']
        }).expect(200);

        const authorize2 = await postConsent(consent);
        expect(authorize2.headers['location']).toBeDefined();
        expect(authorize2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

        const tokenResponse = await performGet(authorize2.headers['location'], '', {
            'Cookie': authorize.headers['set-cookie']
        }).expect(302);

        expect(tokenResponse.headers['location']).toBeDefined();
        return tokenResponse;
    } else {
        return authorize;
    }
}

const createMongoIdp = async (domainId, accessToken) => {
    console.log("creating mongodb  idp")
    return await createIdp(domainId, accessToken, {
        "external": false,
        "type": "mongo-am-idp",
        "domainWhitelist": [],
        "configuration": "{\"uri\":\"mongodb://localhost:27017\",\"host\":\"localhost\",\"port\":27017,\"enableCredentials\":false,\"databaseCredentials\":\"gravitee-am\",\"database\":\"gravitee-am\",\"usersCollection\":\"idp-test-users\",\"findUserByUsernameQuery\":\"{$or: [{username: ?}, {contract: ?}]}\",\"findUserByEmailQuery\":\"{email: ?}\",\"usernameField\":\"username\",\"passwordField\":\"password\",\"passwordEncoder\":\"None\",\"useDedicatedSalt\":false,\"passwordSaltLength\":32}",
        "name": "another-idp"
    });
}

const createJdbcIdp = async (domainId, accessToken) => {
    console.log("creating jdbc idp")
    const password = process.env.GRAVITEE_OAUTH2_JDBC_PASSWORD ? process.env.GRAVITEE_OAUTH2_JDBC_PASSWORD : "p@ssw0rd"
    const database = process.env.GRAVITEE_OAUTH2_JDBC_DATABASE ? process.env.GRAVITEE_OAUTH2_JDBC_DATABASE : "gravitee-am"

    return await createIdp(domainId, accessToken, {
        "external": false,
        "type": "jdbc-am-idp",
        "domainWhitelist": [],
        "configuration": `{\"host\":\"localhost\",\"port\":5432,\"protocol\":\"postgresql\",\"database\":\"${database}\",\"usersTable\":\"test_users\",\"user\":\"postgres\",\"password\":\"${password}\",\"autoProvisioning\":\"true\",\"selectUserByUsernameQuery\":\"SELECT * FROM test_users WHERE username = %s\",\"selectUserByMultipleFieldsQuery\":\"SELECT * FROM test_users WHERE username = %s or email = %s\",\"selectUserByEmailQuery\":\"SELECT * FROM test_users WHERE email = %s\",\"identifierAttribute\":\"id\",\"usernameAttribute\":\"username\",\"emailAttribute\":\"email\",\"passwordAttribute\":\"password\",\"passwordEncoder\":\"None\",\"useDedicatedSalt\":false,\"passwordSaltLength\":32}`,
        "name": "other-jdbc-idp"
    });
}