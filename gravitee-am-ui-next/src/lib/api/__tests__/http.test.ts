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
import { http, HttpError } from '../http';

vi.mock('../../../config/app-config', () => ({ appConfig: () => ({ baseURL: 'http://am.test/management' }) }));

function respond(status: number, body: unknown, headers: Record<string, string> = {}): Response {
    return new Response(status === 204 ? null : JSON.stringify(body), { status, headers });
}

describe('http', () => {
    const fetchMock = vi.fn<typeof fetch>();

    beforeEach(() => {
        fetchMock.mockReset();
        vi.stubGlobal('fetch', fetchMock);
    });

    it('prefixes the Management API root and sends the session cookie', async () => {
        fetchMock.mockResolvedValueOnce(respond(200, { sub: 'u1' }));

        await expect(http('/user')).resolves.toEqual({ sub: 'u1' });

        const [url, init] = fetchMock.mock.calls[0]!;
        expect(url).toBe('http://am.test/management/user');
        expect(init?.credentials).toBe('include');
    });

    it('echoes the CSRF token of the previous response on the next request', async () => {
        fetchMock.mockResolvedValueOnce(respond(200, {}, { 'X-Xsrf-Token': 'token-1' }));
        fetchMock.mockResolvedValueOnce(respond(204, null));

        await http('/user');
        await http('/organizations/DEFAULT/settings', { method: 'PATCH', body: '{}' });

        const headers = new Headers(fetchMock.mock.calls[1]![1]?.headers);
        expect(headers.get('X-Xsrf-Token')).toBe('token-1');
        expect(headers.get('Content-Type')).toBe('application/json');
    });

    it('throws an HttpError that carries the status and the server message', async () => {
        fetchMock.mockResolvedValueOnce(respond(403, { message: 'Permission denied' }));

        const error = await http('/platform/license').catch((caught: unknown) => caught);

        expect(error).toBeInstanceOf(HttpError);
        expect(error).toMatchObject({ status: 403, message: 'Permission denied' });
    });
});
