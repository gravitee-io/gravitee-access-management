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

import fetch from 'cross-fetch';
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest, startDomain } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { createAuthorizationEngine, deleteAuthorizationEngine } from '@management-commands/authorization-engine-management-commands';
import {
    getOpenFgaStore,
    createAuthorizationModel,
    listAuthorizationModels,
    addTuple,
    listTuples,
    deleteTuple,
    checkPermission,
} from '@management-commands/openfga-settings-commands';
import { mcpAuthorizationModel, tupleFactory, checkFactory } from '@api-fixtures/openfga-fixtures';
import { AuthorizationEngine } from '@management-models/AuthorizationEngine';

global.fetch = fetch;

let accessToken: string; // Admin token for management API
let testDomain: any;
let username: 'user123';
let authEngine: AuthorizationEngine;
let storeId: string;
let authorizationModelId: string;

jest.setTimeout(200000);

beforeAll(async () => {
    // 1. Get admin access token
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    // 2. Create unique test domain
    testDomain = await setupDomainForTest(uniqueName('openfga-authzen', true), { accessToken }).then((it) => it.domain);
    expect(testDomain).toBeDefined();

    // 3. Start domain for gateway endpoints
    await startDomain(testDomain.id, accessToken);

    // 4. Create OpenFGA store
    const storeName = `test-store-${Date.now()}`;
    const storeResponse = await fetch(`${process.env.AM_OPENFGA_URL}/stores`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: storeName }),
    });
    expect(storeResponse.status).toBe(201);
    storeId = (await storeResponse.json()).id;

    // 5. Create authorization model
    const modelResponse = await fetch(`${process.env.AM_OPENFGA_URL}/stores/${storeId}/authorization-models`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(mcpAuthorizationModel),
    });
    expect(modelResponse.status).toBe(201);
    authorizationModelId = (await modelResponse.json()).authorization_model_id;

    // 6. Create authorization engine
    authEngine = await createAuthorizationEngine(testDomain.id, accessToken, {
        type: 'openfga',
        name: 'OpenFGA+AuthZen Test Engine',
        configuration: JSON.stringify({
            connectionUri: process.env.AM_OPENFGA_URL,
            storeId,
            authorizationModelId,
            apiToken: '',
        }),
    });
    expect(authEngine?.id).toBeDefined();
});

afterAll(async () => {
    if (authEngine?.id) {
        await deleteAuthorizationEngine(testDomain.id, authEngine.id, accessToken);
    }
    if (testDomain?.id) {
        await safeDeleteDomain(testDomain.id, accessToken);
    }
});

describe('OpenFGA Authorization Engine', () => {

    describe('Management API - Store Management', () => {
        it('should retrieve store metadata', async () => {
            const store = await getOpenFgaStore(testDomain.id, authEngine.id, accessToken);

            expect(store).toBeDefined();
            expect(store.id).toBe(storeId);
            expect(store.name).toBeDefined();
        });
    });

    describe('Management API - Authorization Models', () => {
        it('should list authorization models', async () => {
            const result = await listAuthorizationModels(testDomain.id, authEngine.id, accessToken);

            expect(result).toBeDefined();
            expect(result.data).toBeDefined();
            expect(Array.isArray(result.data)).toBe(true);
            expect(result.data.length).toBeGreaterThan(0);
        });

        it('should list authorization models with pagination', async () => {
            const result = await listAuthorizationModels(testDomain.id, authEngine.id, accessToken, 1);

            expect(result).toBeDefined();
            expect(result.data).toBeDefined();
            expect(result.data.length).toBeLessThanOrEqual(1);
        });

        it('should create a new authorization model', async () => {
            const newModel = {
                schema_version: '1.1',
                type_definitions: [
                    {
                        type: 'user',
                    },
                    {
                        type: 'document',
                        relations: {
                            reader: {
                                this: {},
                            },
                        },
                        metadata: {
                            relations: {
                                reader: {
                                    directly_related_user_types: [{ type: 'user' }],
                                },
                            },
                        },
                    },
                ],
            };

            const result = await createAuthorizationModel(testDomain.id, authEngine.id, accessToken, newModel);
            expect(result).toBeDefined();
            expect(result.authorizationModelId).toBeDefined();
        });
    });

    describe('Management API - Relationship Tuples', () => {
        const testServerId = 'mgmt-api-server-' + Date.now();

        it('should add a relationship tuple', async () => {
            const tuple = tupleFactory.ownerTuple(username, testServerId);

            await addTuple(testDomain.id, authEngine.id, accessToken, tuple);

            // Verify tuple was added
            const tuples = await listTuples(testDomain.id, authEngine.id, accessToken);
            expect(tuples.data).toBeDefined();
            expect(Array.isArray(tuples.data)).toBe(true);

            const foundTuple = tuples.data.find((t) => t.user === tuple.user && t.relation === tuple.relation && t.object === tuple.object);
            expect(foundTuple).toBeDefined();
        });

        it('should list tuples with pagination', async () => {
            const result = await listTuples(testDomain.id, authEngine.id, accessToken, 25);
            expect(result).toBeDefined();
            expect(result.data).toBeDefined();
            expect(Array.isArray(result.data)).toBe(true);
        });

        it('should delete a relationship tuple', async () => {
            const tuple = tupleFactory.viewerTuple(username, testServerId);

            // Add tuple first
            await addTuple(testDomain.id, authEngine.id, accessToken, tuple);

            // Delete the tuple
            await deleteTuple(testDomain.id, authEngine.id, accessToken, tuple);

            // Verify tuple was deleted
            const tuples = await listTuples(testDomain.id, authEngine.id, accessToken);
            const foundTuple = tuples.data.find((t) => t.user === tuple.user && t.relation === tuple.relation && t.object === tuple.object);
            expect(foundTuple).toBeUndefined();
        });
    });

    describe('Management API - Permission Checks', () => {
        const mgmtCheckServerId = 'mgmt-check-server-' + Date.now();

        beforeAll(async () => {
            await addTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.ownerTuple(username, mgmtCheckServerId));
        });

        it('should return true when user has owner relationship and can_access action', async () => {
            const checkRequest = checkFactory.canAccess(username, mgmtCheckServerId);
            const result = await checkPermission(testDomain.id, authEngine.id, accessToken, checkRequest);

            expect(result).toBeDefined();
            expect(result.allowed).toBe(true);
        });

        it('should return true when user has owner relationship and can_manage action', async () => {
            const checkRequest = checkFactory.canManage(username, mgmtCheckServerId);
            const result = await checkPermission(testDomain.id, authEngine.id, accessToken, checkRequest);

            expect(result).toBeDefined();
            expect(result.allowed).toBe(true);
        });

        it('should return false when no relationship tuple exists for user and resource', async () => {
            const unauthorizedServerId = 'unauthorized-server-' + Date.now();
            const checkRequest = checkFactory.canAccess(username, unauthorizedServerId);
            const result = await checkPermission(testDomain.id, authEngine.id, accessToken, checkRequest);

            expect(result).toBeDefined();
            expect(result.allowed).toBe(false);
        });

        it('should return true when user has viewer relationship and can_access action', async () => {
            const viewerServerId = 'viewer-server-' + Date.now();
            const viewerTuple = tupleFactory.viewerTuple(username, viewerServerId);

            await addTuple(testDomain.id, authEngine.id, accessToken, viewerTuple);

            const checkRequest = checkFactory.canAccess(username, viewerServerId);
            const result = await checkPermission(testDomain.id, authEngine.id, accessToken, checkRequest);

            expect(result).toBeDefined();
            expect(result.allowed).toBe(true);
        });

        it('should return false when user has viewer relationship but not can_manage action', async () => {
            const viewerServerId = 'viewer-manage-test-' + Date.now();
            const viewerTuple = tupleFactory.viewerTuple(username, viewerServerId);

            await addTuple(testDomain.id, authEngine.id, accessToken, viewerTuple);

            const checkRequest = checkFactory.canManage(username, viewerServerId);
            const result = await checkPermission(testDomain.id, authEngine.id, accessToken, checkRequest);

            expect(result).toBeDefined();
            expect(result.allowed).toBe(false);
        });
    });
});
