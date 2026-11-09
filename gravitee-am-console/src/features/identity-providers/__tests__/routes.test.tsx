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
import { getIdentityProvider, listIdentityProviderPlugins } from '../../../lib/api/identity-providers';
import { identityProviderRoutes } from '../routes';
import { currentDomain, providersPath, renderRoutes } from './render';

vi.mock('../../../lib/api/identity-providers', async importOriginal => ({
    ...(await importOriginal<object>()),
    getIdentityProvider: vi.fn(),
    listIdentityProviderPlugins: vi.fn(),
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

const LIST = 'domain_identity_provider_list';

function renderAt(path: string, permissions: string[]) {
    return renderRoutes(path, <Route path="/environments/dev/domains/acme/settings">{identityProviderRoutes}</Route>, permissions);
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listIdentityProviderPlugins).mockResolvedValue([]);
});

describe('identityProviderRoutes', () => {
    it('shows the not allowed panel in place of the wizard without the create permission', async () => {
        renderAt(`${providersPath}/new`, [LIST]);

        expect(await screen.findByText('Not allowed')).toBeInTheDocument();
        expect(listIdentityProviderPlugins).not.toHaveBeenCalled();
    });

    it('shows the not allowed panel in place of a provider without the read permission', async () => {
        renderAt(`${providersPath}/idp-1/settings`, [LIST]);

        expect(await screen.findByText('Not allowed')).toBeInTheDocument();
        expect(getIdentityProvider).not.toHaveBeenCalled();
    });
});
