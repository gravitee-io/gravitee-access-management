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
import { appConfig, loadConfig } from '../app-config';

function respond(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), { status });
}

describe('loadConfig', () => {
    const fetchMock = vi.fn<typeof fetch>();

    beforeEach(() => {
        fetchMock.mockReset();
        vi.stubGlobal('fetch', fetchMock);
    });

    it('merges constants.json over build.json', async () => {
        fetchMock.mockImplementation(async input =>
            String(input).endsWith('constants.json')
                ? respond(200, { baseURL: '/management' })
                : respond(200, { version: '4.14.0', baseURL: '/ignored' }),
        );

        await loadConfig();

        expect(appConfig()).toEqual({ baseURL: '/management', version: '4.14.0' });
    });

    it('fails when a config file cannot be read', async () => {
        fetchMock.mockImplementation(async input =>
            String(input).endsWith('constants.json') ? respond(404, {}) : respond(200, { version: '4.14.0' }),
        );

        await expect(loadConfig()).rejects.toThrow('Could not load constants.json: HTTP 404');
    });
});
