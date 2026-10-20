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
import { compareSync } from 'bcryptjs';
import { http } from '../http';
import { serializeConfiguration, updateIdentityProvider } from '../identity-providers';

vi.mock('../http', () => ({ http: vi.fn() }));

describe('serializeConfiguration', () => {
    it('hashes the new passwords of an inline provider with the BCrypt encoder', async () => {
        const configuration = {
            passwordEncoder: 'BCrypt',
            users: [
                { username: 'new', password: 'secret' },
                { username: 'kept', password: '********' },
                { username: 'hashed', password: '$2a$10$abcdefghijklmnopqrstuv' },
                { username: 'hashed-b', password: '$2b$10$abcdefghijklmnopqrstuv' },
            ],
        };

        const users = (JSON.parse(await serializeConfiguration('inline-am-idp', configuration)) as typeof configuration).users;

        expect(compareSync('secret', users[0].password)).toBe(true);
        expect(users.slice(1)).toEqual(configuration.users.slice(1));
    });

    it('sends the configuration of any other provider as it is', async () => {
        const configuration = { passwordEncoder: 'BCrypt', users: [{ username: 'john', password: 'secret' }] };

        expect(await serializeConfiguration('mongo-am-idp', configuration)).toBe(JSON.stringify(configuration));
        expect(await serializeConfiguration('inline-am-idp', { ...configuration, passwordEncoder: 'None' })).toBe(
            JSON.stringify({ ...configuration, passwordEncoder: 'None' }),
        );
    });
});

describe('updateIdentityProvider', () => {
    it('sends an empty configuration for a system provider, which the API returns without one', async () => {
        const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };

        await updateIdentityProvider(ref, {
            id: 'default-idp-d1',
            name: 'Default',
            type: 'mongo-am-idp',
            system: true,
            external: false,
            mappers: {},
        });

        expect(JSON.parse(vi.mocked(http).mock.calls[0][1]!.body as string)).toEqual({
            name: 'Default',
            type: 'mongo-am-idp',
            configuration: '{}',
            mappers: {},
        });
    });
});
