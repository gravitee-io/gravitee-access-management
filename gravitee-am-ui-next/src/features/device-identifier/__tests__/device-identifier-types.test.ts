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
import { deviceIdentifierTypeName, hasNoSettings } from '../device-identifier-types';

describe('deviceIdentifierTypeName', () => {
    it('names the FingerprintJS plugins as the Angular map does', () => {
        expect(deviceIdentifierTypeName('fingerprintjs-v3-community-device-identifier')).toBe('FingerprintJS v3 community');
        expect(deviceIdentifierTypeName('fingerprintjs-v3-pro-device-identifier')).toBe('FingerprintJS v3 Pro');
    });

    it('falls back to the type, or to the fallback given', () => {
        expect(deviceIdentifierTypeName('cookie-device-identifier')).toBe('cookie-device-identifier');
        expect(deviceIdentifierTypeName('cookie-device-identifier', 'Cookie')).toBe('Cookie');
    });
});

describe('hasNoSettings', () => {
    it('is true for a schema without properties', () => {
        expect(hasNoSettings({ type: 'object', properties: {} })).toBe(true);
        expect(hasNoSettings({})).toBe(true);
    });

    it('is false for a schema with a property', () => {
        expect(hasNoSettings({ properties: { useCookie: { type: 'boolean' } } })).toBe(false);
    });
});
