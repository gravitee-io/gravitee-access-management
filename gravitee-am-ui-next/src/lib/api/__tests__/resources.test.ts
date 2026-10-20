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
import type * as httpModule from '../http';
import { HttpError, http } from '../http';
import { listResourcesWhenPermitted } from '../resources';

vi.mock('../http', async importOriginal => ({ ...(await importOriginal<typeof httpModule>()), http: vi.fn() }));

const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };

describe('listResourcesWhenPermitted', () => {
    it('returns the resources of the domain', async () => {
        vi.mocked(http).mockResolvedValueOnce([{ id: 'r-1', name: 'Mail', type: 'smtp-am-resource' }]);

        await expect(listResourcesWhenPermitted(ref)).resolves.toEqual([{ id: 'r-1', name: 'Mail', type: 'smtp-am-resource' }]);
        expect(http).toHaveBeenCalledWith('/organizations/DEFAULT/environments/env-1/domains/d1/resources');
    });

    it('counts a failed list as no resources, as Angular does', async () => {
        vi.mocked(http).mockRejectedValueOnce(new HttpError(403, 'Forbidden'));

        await expect(listResourcesWhenPermitted(ref)).resolves.toEqual([]);
    });

    it('lets a 401 through to the login chain', async () => {
        vi.mocked(http).mockRejectedValueOnce(new HttpError(401, 'Unauthorized'));

        await expect(listResourcesWhenPermitted(ref)).rejects.toThrow('Unauthorized');
    });
});
