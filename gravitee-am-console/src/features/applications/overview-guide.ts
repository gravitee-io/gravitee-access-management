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
/** Drops the `{#…}` expression language parameters, which a client cannot send as they are. */
export function cleanELParameters(redirectUri: string): string {
    let cleaned = redirectUri.replace(/([^={}?&]*=)?\{#[^={}?&]*}&?/g, '');
    if (cleaned.endsWith('&')) cleaned = cleaned.slice(0, -1);
    if (cleaned.endsWith('?')) cleaned = cleaned.slice(0, -1);
    return cleaned;
}

/** The authorization code exchange sends the secret in the body for `client_secret_post` and when no method is set. */
export function exchangesWithPost(tokenEndpointAuthMethod: string | undefined): boolean {
    return !tokenEndpointAuthMethod || tokenEndpointAuthMethod === 'client_secret_post';
}

/** The client credentials sample uses basic authentication for `client_secret_basic` and when no method is set. */
export function requestsWithBasic(tokenEndpointAuthMethod: string | undefined): boolean {
    return !tokenEndpointAuthMethod || tokenEndpointAuthMethod === 'client_secret_basic';
}

const CHARSET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';

function base64Url(text: string): string {
    return btoa(text).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
}

export interface Pkce {
    readonly verifier: string;
    readonly challenge: string;
}

/** A fresh code verifier and its S256 code challenge. */
export async function createPkce(): Promise<Pkce> {
    const bytes = crypto.getRandomValues(new Uint8Array(32));
    const verifier = base64Url(Array.from(bytes, byte => CHARSET[byte % CHARSET.length]).join(''));
    const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier)));
    return { verifier, challenge: base64Url(String.fromCharCode(...digest)) };
}
