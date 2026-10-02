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
import { appConfig } from '../../config/app-config';

export class HttpError extends Error {
    constructor(
        readonly status: number,
        message: string,
    ) {
        super(message);
    }
}

// The Management API rotates the CSRF token on every response and expects the last one back on mutations.
let xsrfToken: string | null = null;

/** Calls the Management API with the session cookie. `path` is relative to `baseURL`, e.g. `/user`. */
async function send(path: string, init: RequestInit): Promise<Response> {
    const headers = new Headers(init.headers);
    if (xsrfToken) {
        headers.set('X-Xsrf-Token', xsrfToken);
    }
    if (init.body && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json');
    }

    const response = await fetch(`${appConfig().baseURL}${path}`, { ...init, headers, credentials: 'include' });

    const nextToken = response.headers.get('X-Xsrf-Token');
    if (nextToken) {
        xsrfToken = nextToken;
    }

    if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new HttpError(response.status, body.message ?? response.statusText);
    }
    return response;
}

export async function http<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await send(path, init);
    return response.status === 204 ? (undefined as T) : response.json();
}

export async function httpText(path: string): Promise<string> {
    return (await send(path, {})).text();
}
