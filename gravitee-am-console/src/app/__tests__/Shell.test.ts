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
import type { Domain } from '../../lib/api/domains';
import { pickCurrentDomain } from '../Shell';

const domains: Domain[] = [
    { id: 'd1', hrid: 'one', name: 'One', enabled: true },
    { id: 'd2', hrid: 'two', name: 'Two', enabled: true },
    { id: 'd3', hrid: 'three', name: 'Three', enabled: false },
];

describe('pickCurrentDomain', () => {
    it('takes the domain in the URL first', () => {
        expect(pickCurrentDomain(domains, 'three', 'two', 'd1')?.id).toBe('d3');
    });

    it('takes the last visited domain, then the default domain, then the first domain', () => {
        expect(pickCurrentDomain(domains, undefined, 'two', 'd3')?.id).toBe('d2');
        expect(pickCurrentDomain(domains, undefined, undefined, 'd3')?.id).toBe('d3');
        expect(pickCurrentDomain(domains, undefined, undefined, null)?.id).toBe('d1');
    });

    it('skips a last visited or default domain that is no longer in the list', () => {
        expect(pickCurrentDomain(domains, undefined, 'gone', 'd2')?.id).toBe('d2');
        expect(pickCurrentDomain(domains, undefined, 'gone', 'gone-too')?.id).toBe('d1');
    });

    it('has no current domain in an environment without domains', () => {
        expect(pickCurrentDomain([], undefined, undefined, 'd1')).toBeUndefined();
    });
});
