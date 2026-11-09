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
import { compare } from 'bcryptjs';
import { http } from '../http';
import {
    getIdentityProvider,
    getIdentityProviderSchema,
    type IdentityProvider,
    listIdentityProviderPlugins,
    serializeConfiguration,
    updateIdentityProvider,
} from '../identity-providers';
import { listAllRoles } from '../roles';

vi.mock('../http', () => ({ http: vi.fn() }));

const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };
const domainPath = '/organizations/DEFAULT/environments/env-1/domains/d1';

describe('identity providers api', () => {
    beforeEach(() => {
        vi.mocked(http).mockReset();
    });

    it('encodes the provider id in the path', async () => {
        vi.mocked(http).mockResolvedValueOnce({});

        await getIdentityProvider(ref, '../certificates');

        expect(vi.mocked(http).mock.calls[0][0]).toBe(`${domainPath}/identities/..%2Fcertificates`);
    });

    it('sends the seven update fields, with an empty configuration for a system provider', async () => {
        vi.mocked(http).mockResolvedValueOnce({});
        const provider: IdentityProvider = {
            id: 'default-idp-d1',
            name: 'Default',
            type: 'mongo-am-idp',
            system: true,
            external: false,
            domainWhitelist: [],
            mappers: { email: 'mail' },
            roleMapper: { r1: ['uid=john'] },
            groupMapper: { g1: ['uid=jane'] },
            createdAt: 1,
            passwordPolicy: 'pp-1',
        };

        await updateIdentityProvider(ref, provider);

        const [path, init] = vi.mocked(http).mock.calls[0];
        expect(path).toBe(`${domainPath}/identities/default-idp-d1`);
        expect(init?.method).toBe('PUT');
        expect(JSON.parse(init?.body as string)).toEqual({
            name: 'Default',
            type: 'mongo-am-idp',
            configuration: '{}',
            domainWhitelist: [],
            mappers: { email: 'mail' },
            roleMapper: { r1: ['uid=john'] },
            groupMapper: { g1: ['uid=jane'] },
        });
    });

    it('lists the built-in plugins, then the external ones, with their expanded fields', async () => {
        vi.mocked(http)
            .mockResolvedValueOnce([{ id: 'mongo-am-idp', name: 'MongoDB' }])
            .mockResolvedValueOnce([{ id: 'github-am-idp', name: 'GitHub' }]);

        await expect(listIdentityProviderPlugins()).resolves.toEqual([
            { id: 'mongo-am-idp', name: 'MongoDB', external: false },
            { id: 'github-am-idp', name: 'GitHub', external: true },
        ]);
        expect(http).toHaveBeenCalledWith('/platform/plugins/identities?expand=icon&expand=displayName&expand=labels');
        expect(http).toHaveBeenCalledWith('/platform/plugins/identities?external=true&expand=icon&expand=displayName&expand=labels');
    });

    it('reads a 204 schema as a schema without fields', async () => {
        vi.mocked(http).mockResolvedValueOnce(undefined);

        await expect(getIdentityProviderSchema('ldap-am-idp')).resolves.toEqual({});
        expect(http).toHaveBeenCalledWith('/platform/plugins/identities/ldap-am-idp/schema');
    });
});

describe('serializeConfiguration', () => {
    it('hashes the new passwords of an inline BCrypt provider, and keeps the hashed and the masked ones', async () => {
        const hashed = '$2a$10$abcdefghijklmnopqrstuuJ8m9yG0w8bQ1kM8vU3fK7oR2nZx3yq';
        const serialized = JSON.parse(
            await serializeConfiguration('inline-am-idp', {
                passwordEncoder: 'BCrypt',
                users: [
                    { username: 'new', password: 'Secret#1' },
                    { username: 'kept', password: hashed },
                    { username: 'masked', password: '********' },
                ],
            }),
        ) as { users: { username: string; password: string }[] };

        const [created, kept, masked] = serialized.users;
        expect(created.password).toMatch(/^\$2b\$10\$/);
        await expect(compare('Secret#1', created.password)).resolves.toBe(true);
        expect(kept.password).toBe(hashed);
        expect(masked.password).toBe('********');
    });

    it('leaves the passwords of an inline provider with another encoder, and of another plugin, as typed', async () => {
        const plain = { passwordEncoder: 'None', users: [{ username: 'john', password: 'Secret#1' }] };

        await expect(serializeConfiguration('inline-am-idp', plain)).resolves.toBe(JSON.stringify(plain));
        await expect(serializeConfiguration('mongo-am-idp', { ...plain, passwordEncoder: 'BCrypt' })).resolves.toBe(
            JSON.stringify({ ...plain, passwordEncoder: 'BCrypt' }),
        );
    });
});

describe('listAllRoles', () => {
    beforeEach(() => {
        vi.mocked(http).mockReset();
    });

    it('reads every page of 50 roles', async () => {
        const role = (id: string) => ({ id, name: id });
        vi.mocked(http)
            .mockResolvedValueOnce({ data: [role('r1')], currentPage: 0, totalCount: 51 })
            .mockResolvedValueOnce({ data: [role('r51')], currentPage: 1, totalCount: 51 });

        await expect(listAllRoles(ref)).resolves.toEqual([role('r1'), role('r51')]);
        expect(http).toHaveBeenCalledWith(`${domainPath}/roles?page=0&size=50`);
        expect(http).toHaveBeenCalledWith(`${domainPath}/roles?page=1&size=50`);
    });
});
