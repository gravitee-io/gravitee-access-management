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
import { cleanELParameters, createPkce, exchangesWithPost, requestsWithBasic } from '../overview-guide';

describe('cleanELParameters', () => {
    it('drops expression language parameters and the separators they leave', () => {
        expect(cleanELParameters('https://app/cb?tenant={#context.attributes["t"]}')).toBe('https://app/cb');
        expect(cleanELParameters('https://app/cb?a=1&tenant={#request.params}&b=2')).toBe('https://app/cb?a=1&b=2');
        expect(cleanELParameters('https://app/cb?a=1&tenant={#request.params}')).toBe('https://app/cb?a=1');
    });

    it('keeps a URI with no expression', () => {
        expect(cleanELParameters('https://app/cb?a=1')).toBe('https://app/cb?a=1');
    });
});

describe('token endpoint samples', () => {
    it('exchanges the code with the secret in the body by default and for client_secret_post', () => {
        expect(exchangesWithPost(undefined)).toBe(true);
        expect(exchangesWithPost('client_secret_post')).toBe(true);
        expect(exchangesWithPost('client_secret_basic')).toBe(false);
        expect(exchangesWithPost('none')).toBe(false);
    });

    it('requests client credentials with basic authentication by default and for client_secret_basic', () => {
        expect(requestsWithBasic(undefined)).toBe(true);
        expect(requestsWithBasic('client_secret_basic')).toBe(true);
        expect(requestsWithBasic('client_secret_post')).toBe(false);
    });
});

describe('createPkce', () => {
    it('derives the S256 challenge from a URL-safe verifier', async () => {
        const { verifier, challenge } = await createPkce();
        const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier)));
        const expected = btoa(String.fromCharCode(...digest))
            .replace(/\+/g, '-')
            .replace(/\//g, '_')
            .replace(/=/g, '');

        expect(verifier).toMatch(/^[A-Za-z0-9_-]{43,}$/);
        expect(challenge).toBe(expected);
    });
});
