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
import { toast } from '@gravitee/graphene-core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { HttpError } from '../../../lib/api/http';
import { getCurrentUser } from '../../../lib/api/management-api';
import { LoginCallbackPage, LogoutCallbackPage } from '../AuthPages';

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/management-api', () => ({ getCurrentUser: vi.fn() }));

function renderAt(path: string, queryClient = new QueryClient()) {
    return render(
        <QueryClientProvider client={queryClient}>
            <MemoryRouter initialEntries={[path]}>
                <Routes>
                    <Route path="/" element={<p>home</p>} />
                    <Route path="/login" element={<p>login</p>} />
                    <Route path="/login/callback" element={<LoginCallbackPage />} />
                    <Route path="/logout" element={<p>logout</p>} />
                    <Route path="/logout/callback" element={<LogoutCallbackPage />} />
                </Routes>
            </MemoryRouter>
        </QueryClientProvider>,
    );
}

describe('LoginCallbackPage', () => {
    it('confirms the session, then goes home with a toast', async () => {
        vi.mocked(getCurrentUser).mockResolvedValue({ sub: 'admin', org: 'DEFAULT', permissions: [] });

        renderAt('/login/callback');

        expect(await screen.findByText('home')).toBeInTheDocument();
        expect(toast.success).toHaveBeenCalledWith('Login successful');
    });

    it('logs out with the error as a toast when the session is not valid', async () => {
        vi.mocked(getCurrentUser).mockRejectedValue(new HttpError(401, 'Unauthorized'));

        renderAt('/login/callback');

        expect(await screen.findByText('logout')).toBeInTheDocument();
        expect(toast.error).toHaveBeenCalledWith('Unauthorized');
    });
});

describe('LogoutCallbackPage', () => {
    it('drops the cached session, then goes to the login page', async () => {
        const queryClient = new QueryClient();
        queryClient.setQueryData(['session'], { user: { sub: 'admin' } });

        renderAt('/logout/callback', queryClient);

        expect(await screen.findByText('login')).toBeInTheDocument();
        expect(queryClient.getQueryData(['session'])).toBeUndefined();
    });
});
