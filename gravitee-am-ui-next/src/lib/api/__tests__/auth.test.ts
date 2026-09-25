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
import { redirectToLogin, redirectToLogout, takeReturnTo } from '../auth';

vi.mock('../../../config/app-config', () => ({ appConfig: () => ({ baseURL: 'http://am.test/management' }) }));

describe('auth redirects', () => {
    const assign = vi.fn();

    beforeEach(() => {
        assign.mockReset();
        sessionStorage.clear();
        vi.stubGlobal('location', { ...window.location, assign });
    });

    it('sends the browser to /auth/authorize with the console callback and keeps the return path', () => {
        redirectToLogin('/environments/dev/domains');

        expect(assign).toHaveBeenCalledWith(
            `http://am.test/management/auth/authorize?redirect_uri=${encodeURIComponent('http://localhost:3000/login/callback')}`,
        );
        expect(takeReturnTo()).toBe('/environments/dev/domains');
    });

    it('returns to the root when no return path is stored or it points at the login route', () => {
        expect(takeReturnTo()).toBe('/');

        redirectToLogin('/login/callback');
        expect(takeReturnTo()).toBe('/');
    });

    it('sends the browser to /auth/logout with the console logout callback', () => {
        redirectToLogout();

        expect(assign).toHaveBeenCalledWith(
            `http://am.test/management/auth/logout?target_url=${encodeURIComponent('http://localhost:3000/logout/callback')}`,
        );
    });
});
