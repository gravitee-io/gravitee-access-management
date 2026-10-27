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
import { getDomain, listDomains } from '../domains';
import { http } from '../http';

vi.mock('../http', () => ({ http: vi.fn() }));

const domain = (id: string) => ({ id, hrid: id, name: id, enabled: true });

describe('domains api', () => {
    beforeEach(() => {
        vi.mocked(http).mockReset();
    });

    it('lists the domains of the environment in pages of 200', async () => {
        vi.mocked(http).mockResolvedValueOnce({ data: [domain('d1')], currentPage: 0, totalCount: 1 });

        await expect(listDomains('DEFAULT', 'env-1')).resolves.toEqual({ data: [domain('d1')], currentPage: 0, totalCount: 1 });

        expect(http).toHaveBeenCalledOnce();
        expect(http).toHaveBeenCalledWith('/organizations/DEFAULT/environments/env-1/domains?page=0&size=200');
    });

    it('reads the next pages until it holds every domain', async () => {
        vi.mocked(http)
            .mockResolvedValueOnce({ data: [domain('d1'), domain('d2')], currentPage: 0, totalCount: 3 })
            .mockResolvedValueOnce({ data: [domain('d3')], currentPage: 1, totalCount: 3 });

        const result = await listDomains('DEFAULT', 'env-1');

        expect(result.data.map(item => item.id)).toEqual(['d1', 'd2', 'd3']);
        expect(http).toHaveBeenLastCalledWith('/organizations/DEFAULT/environments/env-1/domains?page=1&size=200');
    });

    it('stops at an empty page when the total count is too high', async () => {
        vi.mocked(http)
            .mockResolvedValueOnce({ data: [domain('d1')], currentPage: 0, totalCount: 5 })
            .mockResolvedValueOnce({ data: [], currentPage: 1, totalCount: 5 });

        const result = await listDomains('DEFAULT', 'env-1');

        expect(result.data.map(item => item.id)).toEqual(['d1']);
        expect(http).toHaveBeenCalledTimes(2);
    });

    it('reads a domain by its id', async () => {
        await getDomain('DEFAULT', 'env-1', 'd1');

        expect(http).toHaveBeenCalledWith('/organizations/DEFAULT/environments/env-1/domains/d1');
    });
});
