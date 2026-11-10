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
import type { SpelGrammar } from '../../../lib/api/management-api';
import { spelCompletions } from '../idp-spel-completion';

const grammar: SpelGrammar = {
    request: { headers: { _type: 'HttpHeaders' }, params: { _type: 'MultiValueMap' }, path: { _type: 'String' } },
    profile: { _type: 'Map' },
    context: { attributes: { application: { _type: 'String' }, 'user-id': { _type: 'String' } } },
    _enums: { HttpHeaders: ['Accept', 'Authorization', 'Content-Type'] },
};

const labels = (text: string) => spelCompletions(grammar, text).items.map(item => item.label);

describe('spelCompletions', () => {
    it('offers the roots after a hash', () => {
        expect(labels('{#')).toEqual(['request', 'profile', 'context']);
        expect(spelCompletions(grammar, '{#pro')).toEqual({
            prefix: 'pro',
            items: [{ label: 'profile', insertText: 'profile', detail: 'Map' }],
        });
    });

    it('offers the properties of a path', () => {
        expect(labels('{#request.')).toEqual(['headers', 'params', 'path']);
        expect(labels('{#request.pa')).toEqual(['params', 'path']);
        expect(labels('{#context.attributes.')).toEqual(['application', 'user-id']);
    });

    it('offers the known values of a leaf type inside brackets', () => {
        expect(spelCompletions(grammar, "{#request.headers['Au")).toEqual({
            prefix: 'Au',
            items: [{ label: 'Authorization', insertText: 'Authorization', detail: 'HttpHeaders' }],
        });
    });

    it('offers nothing outside an expression or below a leaf', () => {
        expect(labels('username')).toEqual([]);
        expect(labels('{#request.path.')).toEqual([]);
        expect(labels('{#unknown.')).toEqual([]);
    });
});
