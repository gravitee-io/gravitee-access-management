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
import { screen } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { listFactors } from '../../../lib/api/factors';
import { FactorsPage } from '../FactorsPage';
import { currentDomain, factorsPath, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/factors', () => ({ listFactors: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

const ALL = ['domain_factor_create', 'domain_factor_read'];

function renderPage(permissions = ALL) {
    return renderRoutes(factorsPath, <Route path={factorsPath} element={<FactorsPage />} />, permissions);
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listFactors).mockResolvedValue([
        { id: 'f-1', name: 'Email OTP', factorType: 'EMAIL' },
        { id: 'f-2', name: 'Authenticator', factorType: 'OTP' },
    ]);
});

describe('FactorsPage', () => {
    it('lists the factors of the domain with a link to each', async () => {
        renderPage();

        expect(await screen.findByRole('link', { name: 'Email OTP' })).toHaveAttribute('href', `${factorsPath}/f-1`);
        expect(screen.getByRole('link', { name: 'Authenticator' })).toBeInTheDocument();
        expect(screen.getByText('EMAIL')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Add factor' })).toHaveAttribute('href', `${factorsPath}/new`);
        expect(listFactors).toHaveBeenCalledWith(ref);
    });

    it('shows the first-use state when the domain has no factor', async () => {
        vi.mocked(listFactors).mockResolvedValueOnce([]);
        renderPage();

        expect(await screen.findByText('No factors yet')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Add factor' })).toBeInTheDocument();
    });

    it('shows the error panel, not the first-use state, when the list fails', async () => {
        vi.mocked(listFactors).mockRejectedValueOnce(new Error('Service unavailable'));
        renderPage();

        expect(await screen.findByText('Could not load the factors.')).toBeInTheDocument();
        expect(screen.queryByText('No factors yet')).not.toBeInTheDocument();
    });

    it('hides the add button and the links without the permissions', async () => {
        renderPage([]);

        expect(await screen.findByText('Email OTP')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Email OTP' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Add factor' })).not.toBeInTheDocument();
    });
});
