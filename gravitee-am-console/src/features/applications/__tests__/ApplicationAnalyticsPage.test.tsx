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
import { selectHarness } from '@gravitee/graphene-core/testing';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { type AnalyticsQuery, searchApplicationAnalytics } from '../../../lib/api/analytics';
import { ApplicationAnalyticsPage } from '../ApplicationAnalyticsPage';
import { ApplicationContext } from '../ApplicationContext';
import { applicationsPath, currentDomain, ref, renderRoutes, webApplication } from './render';

vi.mock('../../../lib/api/analytics', () => ({ searchApplicationAnalytics: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

const appPath = `${applicationsPath}/app-1`;
const COUNTS: Record<string, number> = { user_login: 42, user_registered: 7 };

function respond(query: AnalyticsQuery) {
    if (query.type === 'count') return Promise.resolve({ value: COUNTS[query.field] });
    return Promise.resolve({ timestamp: { from: query.from, to: query.to, interval: query.interval }, values: [] });
}

function renderPage() {
    return renderRoutes(
        `${appPath}/analytics`,
        <Route
            path={`${appPath}/analytics`}
            element={
                <ApplicationContext value={{ application: webApplication, applicationPath: appPath, listPath: applicationsPath }}>
                    <ApplicationAnalyticsPage />
                </ApplicationContext>
            }
        />,
        ['application_analytics_list'],
    );
}

const calls = () => vi.mocked(searchApplicationAnalytics).mock.calls;

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(searchApplicationAnalytics).mockImplementation((_, __, query) => respond(query));
});

describe('ApplicationAnalyticsPage', () => {
    it('reads the login and sign-up counts and histograms of the application over the last 24 hours', async () => {
        renderPage();

        expect(await screen.findByText('+42')).toBeInTheDocument();
        expect(screen.getByText('+7')).toBeInTheDocument();
        expect(calls().map(([, , query]) => `${query.type}:${query.field}`)).toEqual([
            'count:user_login',
            'count:user_registered',
            'date_histo:user_login',
            'date_histo:user_registered',
        ]);
        const [domainRef, applicationId, query] = calls()[0];
        expect(domainRef).toEqual(ref);
        expect(applicationId).toBe('app-1');
        expect(query.interval).toBe(3_600_000);
        expect(query.to - query.from).toBe(24 * 3_600_000);
    });

    it('reads again over the chosen time range', async () => {
        renderPage();
        await screen.findByText('+42');

        await selectHarness({ name: 'Time range' }).selectOption('Last 12 hours');

        await waitFor(() => expect(calls()).toHaveLength(8));
        const query = calls()[4][2];
        expect(query.to - query.from).toBe(12 * 3_600_000);
    });

    it('runs every call again on refresh', async () => {
        renderPage();
        await screen.findByText('+42');

        fireEvent.click(screen.getByRole('button', { name: 'Refresh data' }));

        await waitFor(() => expect(calls()).toHaveLength(8));
    });

    it('shows the error panel when every call fails', async () => {
        vi.mocked(searchApplicationAnalytics).mockRejectedValue(new Error('Service unavailable'));
        renderPage();

        expect(await screen.findByText('Service unavailable')).toBeInTheDocument();
    });
});
