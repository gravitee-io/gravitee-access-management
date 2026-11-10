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
import { inputHarness } from '@gravitee/graphene-core/testing';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route } from 'react-router-dom';
import { type ApplicationSummaryRow, listDomainApplications } from '../../../lib/api/applications';
import { HttpError } from '../../../lib/api/http';
import { ApplicationsPage } from '../ApplicationsPage';
import { applicationsPath, currentDomain, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/applications', () => ({ listDomainApplications: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

const rows: ApplicationSummaryRow[] = [
    { id: 'a1', name: 'Portal', type: 'web', enabled: true, updatedAt: 1_700_000_000_000 },
    { id: 'a2', name: 'Batch', type: 'service', enabled: false },
    { id: 'a3', name: 'Template app', type: 'browser', enabled: true, template: true },
];

const page = (data: ApplicationSummaryRow[], totalCount = data.length) => ({ data, currentPage: 0, totalCount });

function renderPage(permissions = ['application_list', 'application_create']) {
    return renderRoutes(applicationsPath, <Route path={applicationsPath} element={<ApplicationsPage />} />, permissions);
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listDomainApplications).mockResolvedValue(page(rows));
});

describe('ApplicationsPage', () => {
    it('lists the first page of the applications, with their status and type', async () => {
        renderPage();

        expect(await screen.findByRole('link', { name: 'Portal' })).toHaveAttribute('href', `${applicationsPath}/a1`);
        expect(listDomainApplications).toHaveBeenCalledWith(ref, 0, 10, '');
        const table = screen.getByRole('table');
        const statuses = within(table)
            .getAllByRole('row')
            .slice(1)
            .map(row =>
                within(row)
                    .getAllByRole('cell')
                    .map(cell => cell.textContent),
            );
        expect(statuses.map(([name, status, type]) => [name, status, type])).toEqual([
            ['Portal', 'Enabled', 'Web'],
            ['Batch', 'Disabled', 'Backend to Backend'],
            ['Template app', 'Template', 'Single-Page App'],
        ]);
    });

    it('searches the applications from the first page', async () => {
        renderPage();
        await screen.findByRole('link', { name: 'Portal' });
        vi.mocked(listDomainApplications).mockResolvedValue(page([], 0));

        await inputHarness({ name: 'Search applications' }).type('port');

        await waitFor(() => expect(listDomainApplications).toHaveBeenLastCalledWith(ref, 0, 10, 'port'));
        expect(await screen.findByText('No applications match your search')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Clear search' }));
        await waitFor(() => expect(listDomainApplications).toHaveBeenLastCalledWith(ref, 0, 10, ''));
    });

    it('shows the first-use state with the create action when the domain has no application', async () => {
        vi.mocked(listDomainApplications).mockResolvedValue(page([], 0));
        renderPage();

        expect(await screen.findByText('No applications yet')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Create application' })).toHaveAttribute('href', `${applicationsPath}/new`);
    });

    it('shows the first-use state without the create action to a user who cannot create', async () => {
        vi.mocked(listDomainApplications).mockResolvedValue(page([], 0));
        renderPage(['application_list']);

        expect(await screen.findByText('No applications yet')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Create application' })).not.toBeInTheDocument();
    });

    it('offers the create action only with application_create', async () => {
        const { unmount } = renderPage();
        await screen.findByRole('link', { name: 'Portal' });
        expect(screen.getByRole('link', { name: 'Create application' })).toHaveAttribute('href', `${applicationsPath}/new`);
        unmount();

        renderPage(['application_list']);
        await screen.findByRole('link', { name: 'Portal' });
        expect(screen.queryByRole('link', { name: 'Create application' })).not.toBeInTheDocument();
    });

    it('shows an error when the applications do not load', async () => {
        vi.mocked(listDomainApplications).mockRejectedValue(new HttpError(500, 'boom'));
        renderPage();

        expect(await screen.findByText('Could not load the applications.')).toBeInTheDocument();
    });
});
