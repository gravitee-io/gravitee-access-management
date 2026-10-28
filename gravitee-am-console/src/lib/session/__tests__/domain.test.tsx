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
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { getDomainByHrid } from '../../api/domains';
import { HttpError } from '../../api/http';
import { listDomainPermissions } from '../../api/permissions';
import { DomainScope, useCurrentDomain } from '../domain';
import { usePermissions } from '../permissions';

vi.mock('../../api/domains', () => ({ getDomainByHrid: vi.fn() }));
vi.mock('../../api/permissions', () => ({ listDomainPermissions: vi.fn() }));
vi.mock('../session', () => ({ useSession: () => ({ user: { org: 'DEFAULT' } }) }));
vi.mock('../environment', () => ({ useCurrentEnvironment: () => ({ id: 'env-1', name: 'Development', hrids: ['dev'] }) }));

function Probe() {
    const { domain, ref, basePath } = useCurrentDomain();
    const { has } = usePermissions();
    return (
        <p>
            {domain.name} {ref.domainId} {basePath} {String(has('domain_analytics_read'))}
        </p>
    );
}

function renderScope() {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return renderWithGraphene(
        <QueryClientProvider client={client}>
            <MemoryRouter initialEntries={['/environments/dev/domains/acme']}>
                <Routes>
                    <Route path="/environments/:envHrid/domains/:domainHrid" element={<DomainScope />}>
                        <Route index element={<Probe />} />
                    </Route>
                </Routes>
            </MemoryRouter>
        </QueryClientProvider>,
    );
}

describe('DomainScope', () => {
    it('reads the domain by hrid and its permissions by id', async () => {
        vi.mocked(getDomainByHrid).mockResolvedValue({ id: 'd1', hrid: 'acme', name: 'Acme', enabled: true });
        vi.mocked(listDomainPermissions).mockResolvedValue(['domain_analytics_read']);

        renderScope();

        expect(await screen.findByText('Acme d1 /environments/dev/domains/acme true')).toBeInTheDocument();
        expect(getDomainByHrid).toHaveBeenCalledWith('DEFAULT', 'env-1', 'acme');
        expect(listDomainPermissions).toHaveBeenCalledWith({ organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' });
    });

    it('shows the not found panel for an unknown hrid', async () => {
        vi.mocked(getDomainByHrid).mockRejectedValue(new HttpError(404, 'Domain not found'));

        renderScope();

        expect(await screen.findByText('Domain not found')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Back to domains' })).toHaveAttribute('href', '/environments/dev/domains');
    });

    it('shows the error panel when the domain cannot be read', async () => {
        vi.mocked(getDomainByHrid).mockRejectedValue(new HttpError(500, 'Boom'));

        renderScope();

        expect(await screen.findByText('Could not load the domain acme.')).toBeInTheDocument();
    });
});
