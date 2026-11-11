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
import { defaultSelectionRule, moveRow, toIdentityProviderLinks, toProviderRows } from '../idp-rows';

const providers = [
    { id: 'ldap', name: 'LDAP', type: 'ldap-am-idp' },
    { id: 'mongo', name: 'Mongo', type: 'mongo-am-idp' },
    { id: 'inline', name: 'Inline', type: 'inline-am-idp' },
];

describe('toProviderRows', () => {
    it('lists the selected providers by priority, then the others in the domain order', () => {
        const rows = toProviderRows(providers, [
            { identity: 'inline', priority: 0, selectionRule: "{#request.params['username'] matches '.+'}" },
            { identity: 'mongo', priority: 1 },
        ]);

        expect(rows).toEqual([
            {
                id: 'inline',
                name: 'Inline',
                type: 'inline-am-idp',
                selected: true,
                selectionRule: "{#request.params['username'] matches '.+'}",
            },
            { id: 'mongo', name: 'Mongo', type: 'mongo-am-idp', selected: true, selectionRule: '' },
            { id: 'ldap', name: 'LDAP', type: 'ldap-am-idp', selected: false, selectionRule: '' },
        ]);
    });
});

describe('toIdentityProviderLinks', () => {
    it('numbers the selected providers of each list from 0, in the list order', () => {
        const internal = [
            { id: 'ldap', name: 'LDAP', type: 'ldap-am-idp', selected: false, selectionRule: '' },
            { id: 'mongo', name: 'Mongo', type: 'mongo-am-idp', selected: true, selectionRule: 'rule-m' },
            { id: 'inline', name: 'Inline', type: 'inline-am-idp', selected: true, selectionRule: '' },
        ];
        const external = [{ id: 'google', name: 'Google', type: 'google-am-idp', selected: true, selectionRule: 'rule-g' }];

        expect(toIdentityProviderLinks(internal, external)).toEqual([
            { identity: 'mongo', selectionRule: 'rule-m', priority: 0 },
            { identity: 'inline', selectionRule: '', priority: 1 },
            { identity: 'google', selectionRule: 'rule-g', priority: 0 },
        ]);
    });
});

describe('moveRow', () => {
    it('moves a row to another position', () => {
        expect(moveRow(['a', 'b', 'c'], 0, 2)).toEqual(['b', 'c', 'a']);
        expect(moveRow(['a', 'b', 'c'], 2, 1)).toEqual(['a', 'c', 'b']);
    });
});

describe('defaultSelectionRule', () => {
    it('matches the user base of the provider type', () => {
        expect(defaultSelectionRule('google-am-idp')).toBe("{#request.params['username'] matches '.+@gmail.com$'}");
        expect(defaultSelectionRule('azure-ad-am-idp')).toBe("{#request.params['username'] matches '.+@microsoft.com$'}");
        expect(defaultSelectionRule('ldap-am-idp')).toBe("{#request.params['username'] matches '.+'}");
    });
});
