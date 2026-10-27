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
import { redirectToLogin } from '../../lib/api/auth';
import { HttpError } from '../../lib/api/http';
import { createQueryClient } from '../App';

vi.mock('../../lib/api/auth', () => ({ redirectToLogin: vi.fn() }));

describe('createQueryClient', () => {
    beforeEach(() => {
        vi.mocked(redirectToLogin).mockReset();
    });

    it('starts the login chain when a query fails with 401', async () => {
        const queryFn = vi.fn().mockRejectedValue(new HttpError(401, 'Unauthorized'));

        await createQueryClient()
            .fetchQuery({ queryKey: ['user'], queryFn })
            .catch(() => undefined);

        expect(redirectToLogin).toHaveBeenCalledOnce();
        expect(queryFn).toHaveBeenCalledOnce();
    });

    it('does not start the login chain for another error', async () => {
        await createQueryClient()
            .fetchQuery({ queryKey: ['user'], queryFn: () => Promise.reject(new HttpError(403, 'Forbidden')) })
            .catch(() => undefined);

        expect(redirectToLogin).not.toHaveBeenCalled();
    });

    it('retries a server or network error once, and never a client error', () => {
        const retry = createQueryClient().getDefaultOptions().queries?.retry;
        if (typeof retry !== 'function') {
            throw new Error('retry is not a function');
        }

        expect(retry(0, new HttpError(503, 'Unavailable'))).toBe(true);
        expect(retry(0, new TypeError('Failed to fetch'))).toBe(true);
        expect(retry(1, new HttpError(503, 'Unavailable'))).toBe(false);
        expect(retry(0, new HttpError(404, 'Not found'))).toBe(false);
    });
});
