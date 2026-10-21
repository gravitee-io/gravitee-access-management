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
import { isHttpFactorResource, resourceTypeName } from '../resource-types';

describe('resourceTypeName', () => {
    it('gives the Angular name of a built-in type, else the fallback', () => {
        expect(resourceTypeName('infobip-am-resource')).toBe('Infobip 2FA');
        expect(resourceTypeName('custom-am-resource')).toBe('custom-am-resource');
        expect(resourceTypeName('custom-am-resource', 'Custom')).toBe('Custom');
    });
});

describe('isHttpFactorResource', () => {
    it('matches the HTTP Factor resource type', () => {
        expect(isHttpFactorResource('http-factor-am-resource')).toBe(true);
        expect(isHttpFactorResource('smtp-am-resource')).toBe(false);
    });
});
