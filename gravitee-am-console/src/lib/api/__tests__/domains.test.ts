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

describe('domains api', () => {
    it('lists one page of 200 domains of the environment', async () => {
        await listDomains('DEFAULT', 'env-1');

        expect(http).toHaveBeenCalledWith('/organizations/DEFAULT/environments/env-1/domains?page=0&size=200');
    });

    it('reads a domain by its id', async () => {
        await getDomain('DEFAULT', 'env-1', 'd1');

        expect(http).toHaveBeenCalledWith('/organizations/DEFAULT/environments/env-1/domains/d1');
    });
});
