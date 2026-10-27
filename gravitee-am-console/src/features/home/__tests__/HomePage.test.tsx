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
import { renderWithGraphene } from '@gravitee/graphene-core/testing';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { HttpError } from '../../../lib/api/http';
import type { Environment } from '../../../lib/api/management-api';
import type { HomePage as HomePageType } from '../HomePage';

const { getDomain, getUserPreferences } = vi.hoisted(() => ({ getDomain: vi.fn(), getUserPreferences: vi.fn() }));
vi.mock('../../../lib/api/domains', () => ({ getDomain }));
vi.mock('../../../lib/api/management-api', () => ({ getUserPreferences }));

// Each case imports a fresh module, which resets the first-visit redirect flag.
let HomePage: typeof HomePageType;

let environments: Environment[] = [];
vi.mock('../../../lib/session/session', () => ({
    useSession: () => ({ user: { sub: 'admin', org: 'DEFAULT', permissions: [] }, environments }),
}));

function Location() {
    return <p>at {useLocation().pathname}</p>;
}

function renderHome(queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
    return renderWithGraphene(
        <QueryClientProvider client={queryClient}>
            <MemoryRouter initialEntries={['/']}>
                <Routes>
                    <Route path="/" element={<HomePage />} />
                    <Route path="*" element={<Location />} />
                </Routes>
            </MemoryRouter>
        </QueryClientProvider>,
    );
}

describe('HomePage', () => {
    beforeEach(async () => {
        vi.clearAllMocks();
        vi.resetModules();
        ({ HomePage } = await import('../HomePage'));
        environments = [
            { id: 'env-1', name: 'Production', hrids: ['prod'] },
            { id: 'env-2', name: 'Staging', hrids: ['staging'] },
        ];
    });

    it('opens the default domain in its environment', async () => {
        vi.mocked(getUserPreferences).mockResolvedValue({ defaultDomainId: 'd7', defaultEnvironmentId: 'env-2' });
        vi.mocked(getDomain).mockResolvedValue({ id: 'd7', hrid: 'seven', name: 'Seven', enabled: true });

        renderHome();

        expect(await screen.findByText('at /environments/staging/domains/seven')).toBeInTheDocument();
        expect(getDomain).toHaveBeenCalledWith('DEFAULT', 'env-2', 'd7');
    });

    it('opens the environment when the default domain no longer exists', async () => {
        vi.mocked(getUserPreferences).mockResolvedValue({ defaultDomainId: 'gone', defaultEnvironmentId: 'env-1' });
        vi.mocked(getDomain).mockRejectedValue(new HttpError(404, 'Domain not found'));

        renderHome();

        expect(await screen.findByText('at /environments/prod')).toBeInTheDocument();
    });

    it('opens the first environment when there is no default domain', async () => {
        vi.mocked(getUserPreferences).mockResolvedValue({});

        renderHome();

        expect(await screen.findByText('at /environments/prod')).toBeInTheDocument();
        expect(getDomain).not.toHaveBeenCalled();
    });

    it('opens the default domain on the first visit only', async () => {
        vi.mocked(getUserPreferences).mockResolvedValue({ defaultDomainId: 'd7', defaultEnvironmentId: 'env-1' });
        vi.mocked(getDomain).mockResolvedValue({ id: 'd7', hrid: 'seven', name: 'Seven', enabled: true });
        const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

        const first = renderHome(queryClient);
        expect(await screen.findByText('at /environments/prod/domains/seven')).toBeInTheDocument();
        first.unmount();

        renderHome(queryClient);
        expect(await screen.findByText('at /environments/prod')).toBeInTheDocument();
    });

    it('explains how to get an environment when the user has none', async () => {
        environments = [];
        vi.mocked(getUserPreferences).mockResolvedValue({});

        renderHome();

        expect(await screen.findByText('No environments')).toBeInTheDocument();
    });
});
