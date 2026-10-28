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
import { LayoutSlotsProvider, TooltipProvider } from '@gravitee/graphene-core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { type AnalyticsQuery, searchAnalytics } from '../../../lib/api/analytics';
import { PermissionScope } from '../../../lib/session/permissions';
import { DomainDashboardPage } from '../DomainDashboardPage';

vi.mock('../../../lib/api/analytics', () => ({ searchAnalytics: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({
    useCurrentDomain: () => ({
        domain: { id: 'd1', hrid: 'acme', name: 'Acme', enabled: true },
        ref,
        basePath: '/environments/dev/domains/acme',
        domainsPath: '/environments/dev/domains',
    }),
}));

const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };
const COUNTS: Record<string, number> = { user_login: 42, user_registered: 0, user: 120, application: 3 };

function respond(query: AnalyticsQuery) {
    if (query.type === 'count') return Promise.resolve({ value: COUNTS[query.field] });
    if (query.type === 'date_histo')
        return Promise.resolve({ timestamp: { from: query.from, to: query.to, interval: query.interval }, values: [] });
    return Promise.resolve({ values: {} });
}

function renderPage(permissions = ['domain_analytics_read', 'application_create']) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={client}>
            <PermissionScope permissions={permissions}>
                <MemoryRouter initialEntries={['/environments/dev/domains/acme/dashboard']}>
                    <LayoutSlotsProvider>
                        <TooltipProvider>
                            <Routes>
                                <Route path="/environments/dev/domains/acme/dashboard" element={<DomainDashboardPage />} />
                            </Routes>
                        </TooltipProvider>
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </PermissionScope>
        </QueryClientProvider>,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(searchAnalytics).mockImplementation((_, query) => respond(query));
});

describe('DomainDashboardPage', () => {
    it('sends one analytics call per Angular widget over the last 24 hours, and shows the counts', async () => {
        renderPage();

        expect(await screen.findByText('+42')).toBeInTheDocument();
        expect(screen.getByText('120')).toBeInTheDocument();
        expect(screen.getByText('3')).toBeInTheDocument();
        expect(searchAnalytics).toHaveBeenCalledTimes(13);
        const login = vi.mocked(searchAnalytics).mock.calls.find(([, query]) => query.type === 'count' && query.field === 'user_login');
        expect(login?.[0]).toEqual(ref);
        expect(login?.[1]).toMatchObject({ interval: 3_600_000, size: undefined });
        expect(login![1].to - login![1].from).toBe(24 * 3_600_000);
        expect(vi.mocked(searchAnalytics).mock.calls.map(([, query]) => `${query.type} ${query.field}`)).toContain('group_by application');
        expect(
            vi.mocked(searchAnalytics).mock.calls.find(([, query]) => query.field === 'application' && query.type === 'group_by')?.[1].size,
        ).toBe(20);
    });

    it('shows the failure in the widget whose call failed only', async () => {
        vi.mocked(searchAnalytics).mockImplementation((_, query) =>
            query.type === 'count' && query.field === 'user' ? Promise.reject(new Error('Timeout')) : respond(query),
        );
        renderPage();

        expect(await screen.findByText('Failed to fetch data')).toBeInTheDocument();
        expect(screen.getByText('+42')).toBeInTheDocument();
    });

    it('shows the error panel when every call fails', async () => {
        vi.mocked(searchAnalytics).mockRejectedValue(new Error('Service unavailable'));
        renderPage();

        expect(await screen.findByText('Unable to load')).toBeInTheDocument();
        expect(screen.getByText('Service unavailable')).toBeInTheDocument();
    });

    it('runs every call again on refresh', async () => {
        renderPage();
        await screen.findByText('+42');

        fireEvent.click(screen.getByRole('button', { name: 'Refresh data' }));

        await waitFor(() => expect(searchAnalytics).toHaveBeenCalledTimes(26));
    });

    it('offers Create application only with application_create', async () => {
        const { unmount } = renderPage();
        expect(await screen.findByRole('link', { name: 'Create application' })).toHaveAttribute(
            'href',
            '/environments/dev/domains/acme/applications/new',
        );
        unmount();

        renderPage(['domain_analytics_read']);
        await screen.findByText('+42');
        expect(screen.queryByRole('link', { name: 'Create application' })).not.toBeInTheDocument();
    });
});
