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
import {afterAll, beforeAll, expect, jest} from "@jest/globals";
import {createDomain, deleteDomain, patchDomain, startDomain} from "@management-commands/domain-management-commands";
import {buildCreateAndTestUser, resetUserPassword} from "@management-commands/user-management-commands";

import {requestAdminAccessToken} from "@management-commands/token-management-commands";
import {ResponseError} from "../../api/management/runtime";

global.fetch = fetch;
jest.setTimeout(200000)

let accessToken;
let domain;
let user;
const passwords = [
    "SomeP@ssw0rd",
    "SomeP@ssw0rd01",
    "SomeP@ssw0rd02",
    "SomeP@ssw0rd03",
];

beforeAll(async () => {
    const adminTokenResponse = await requestAdminAccessToken();
    accessToken = adminTokenResponse.body.access_token;
    domain = await createDomain(accessToken, "domain-ph-users", faker.company.catchPhraseDescriptor()).then(async createdDomain => {
        return await startDomain(createdDomain.id, accessToken);
    });

});

describe("Testing password history...", () => {
    describe("when password history is enabled", () => {
        beforeAll(async () => {
            domain = await patchDomain(domain.id, accessToken, {
                "passwordSettings": {
                    "passwordHistoryEnabled": true,
                    "oldPasswords": 3
                }
            });
            user = await buildCreateAndTestUser(domain.id, accessToken, 0, false);
            await new Promise((r) => setTimeout(r, 1000));
        });

        it(`reset password fails with ${passwords[0]} as it is already in history `, async () => {
            await expect(async () => {
                await resetUserPassword(domain.id, accessToken, user.id, passwords[0]);
            }).rejects.toThrow(ResponseError);
        });
        it(`reset password succeeds with ${passwords[0]}`, async () => {
            expect(async () => {
                await resetUserPassword(domain.id, accessToken, user.id, passwords[1]);
            }).not.toThrow(ResponseError);
        });
        it(`reset password succeeds with ${passwords[1]}`, async () => {
            expect(async () => {
                await resetUserPassword(domain.id, accessToken, user.id, passwords[2]);
            }).not.toThrow(ResponseError);
        });
        it(`reset password succeeds with ${passwords[2]}`, async () => {
            expect(async () => {
                await resetUserPassword(domain.id, accessToken, user.id, passwords[3]);
            }).not.toThrow(ResponseError);
        });

        afterAll(async () => {
            await new Promise((r) => setTimeout(r, 1000));//Delay to prevent domain being cleaned up before reset completes
        })
    });
});

afterAll(async () => {
    if (domain && domain.id) {
        await deleteDomain(domain.id, accessToken);
    }
});