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
import { accessUrl, domainEntrypoint, hostOptions, hostPattern } from '../entrypoint-hosts';

describe('hostPattern', () => {
    it('accepts DNS labels and a port without domain restrictions', () => {
        const pattern = hostPattern([]);
        expect(pattern.test('auth.acme.example:8092')).toBe(true);
        expect(pattern.test('-auth.acme.example')).toBe(false);
        expect(pattern.test('https://auth.acme.example')).toBe(false);
    });

    it('requires one of the domain restrictions as the suffix', () => {
        const pattern = hostPattern(['acme.io', 'acme.eu']);
        expect(pattern.test('auth.acme.io')).toBe(true);
        expect(pattern.test('acme.eu:443')).toBe(true);
        expect(pattern.test('auth.acmexio')).toBe(false);
        expect(pattern.test('auth.other.example')).toBe(false);
    });
});

describe('hostOptions', () => {
    const restrictions = ['acme.io', 'acme.eu'];

    it('lists the restrictions for an empty host or a restriction itself', () => {
        expect(hostOptions('', restrictions)).toEqual(restrictions);
        expect(hostOptions('acme.io', restrictions)).toEqual(restrictions);
    });

    it('completes the typed host with each restriction and keeps the port', () => {
        expect(hostOptions('auth', restrictions)).toEqual(['auth.acme.io', 'auth.acme.eu']);
        expect(hostOptions('auth.acme.io:8092', restrictions)).toEqual(['auth.acme.io:8092', 'auth.acme.eu:8092']);
    });
});

describe('domainEntrypoint', () => {
    it('takes the only entrypoint, else the first one that is not the default', () => {
        const fallback = { id: 'default', url: 'https://gw.example', defaultEntrypoint: true };
        const tagged = { id: 'eu', url: 'https://eu.gw.example', defaultEntrypoint: false };
        expect(domainEntrypoint([fallback])).toBe(fallback);
        expect(domainEntrypoint([fallback, tagged])).toBe(tagged);
        expect(domainEntrypoint([])).toBeUndefined();
    });
});

describe('accessUrl', () => {
    it('joins the entrypoint and the path in context-path mode', () => {
        expect(accessUrl('https://gw.example', '/acme', false, [])).toBe('https://gw.example/acme');
    });

    it('uses the overriding virtual host with the entrypoint scheme in virtual hosts mode', () => {
        const vhosts = [
            { host: 'a.example', path: '/a' },
            { host: 'auth.example', path: '/', overrideEntrypoint: true },
        ];
        expect(accessUrl('https://gw.example', '/acme', true, vhosts)).toBe('https://auth.example');
    });
});
