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
import { redirectUri } from '../redirect-uri';

const domain = { id: 'd1', hrid: 'acme', name: 'Acme', enabled: true, path: '/acme' };

describe('redirectUri', () => {
    it('appends the domain path and the callback to the single entrypoint', () => {
        expect(redirectUri([{ id: 'e1', url: 'https://auth.example.com', defaultEntrypoint: true }], domain)).toBe(
            'https://auth.example.com/acme/login/callback',
        );
    });

    it('prefers a custom entrypoint over the default one', () => {
        const entrypoints = [
            { id: 'e1', url: 'https://default.example.com', defaultEntrypoint: true },
            { id: 'e2', url: 'https://custom.example.com', defaultEntrypoint: false },
        ];

        expect(redirectUri(entrypoints, domain)).toBe('https://custom.example.com/acme/login/callback');
    });

    it('uses the overriding virtual host in vhost mode', () => {
        const vhosts = [{ host: 'login.acme.com', path: '/', overrideEntrypoint: true }];

        expect(redirectUri([{ id: 'e1', url: 'https://auth.example.com' }], { ...domain, vhostMode: true, vhosts })).toBe(
            'https://login.acme.com/login/callback',
        );
    });
});
