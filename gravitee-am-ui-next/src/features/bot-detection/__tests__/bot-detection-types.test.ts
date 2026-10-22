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
import { botDetectionTypeName, configurationSchema, RECAPTCHA_V3, schemaDetectionType } from '../bot-detection-types';

describe('botDetectionTypeName', () => {
    it('names reCAPTCHA v3 as the Angular map does, with its typo corrected', () => {
        expect(botDetectionTypeName(RECAPTCHA_V3)).toBe('Google reCAPTCHA v3');
    });

    it('falls back to the type, or to the fallback given', () => {
        expect(botDetectionTypeName('custom')).toBe('custom');
        expect(botDetectionTypeName('custom', 'Custom plugin')).toBe('Custom plugin');
    });
});

describe('schemaDetectionType', () => {
    it('reads the default of properties.detectionType', () => {
        expect(schemaDetectionType({ properties: { detectionType: { default: 'CAPTCHA' } } })).toBe('CAPTCHA');
    });

    it('is undefined when the schema has no detection type', () => {
        expect(schemaDetectionType({ properties: {} })).toBeUndefined();
        expect(schemaDetectionType({})).toBeUndefined();
    });
});

describe('configurationSchema', () => {
    it('drops properties.detectionType and keeps the other properties', () => {
        const schema = {
            type: 'object',
            properties: { detectionType: { default: 'CAPTCHA' }, siteKey: { type: 'string' } },
            required: ['siteKey'],
        };

        expect(configurationSchema(schema)).toEqual({ type: 'object', properties: { siteKey: { type: 'string' } }, required: ['siteKey'] });
    });

    it('returns a schema without properties as it is', () => {
        expect(configurationSchema({ type: 'object' })).toEqual({ type: 'object' });
    });
});
