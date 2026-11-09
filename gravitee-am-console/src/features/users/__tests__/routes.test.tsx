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
import { listUserIdentityProviders } from '../../../lib/api/identity-providers';
import { getUser, listUsers } from '../../../lib/api/users';
import { userRoutes } from '../routes';
import { currentDomain, renderRoutes, usersPath } from './render';

vi.mock('../../../lib/api/users', async importOriginal => ({
    ...(await importOriginal<object>()),
    getUser: vi.fn(),
    listUsers: vi.fn(),
}));
vi.mock('../../../lib/api/identity-providers', () => ({ listIdentityProviders: vi.fn(), listUserIdentityProviders: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

function renderAt(path: string, permissions: string[]) {
    return renderRoutes(path, <Route path="/environments/dev/domains/acme/settings">{userRoutes}</Route>, permissions);
}

beforeEach(() => vi.clearAllMocks());

describe('userRoutes', () => {
    it('shows the not allowed panel in place of the list without the list permission', async () => {
        renderAt(usersPath, []);

        expect(await screen.findByText('Not allowed')).toBeInTheDocument();
        expect(listUsers).not.toHaveBeenCalled();
    });

    it('shows the not allowed panel in place of the creation form without the create permission', async () => {
        renderAt(`${usersPath}/new`, ['domain_user_list']);

        expect(await screen.findByText('Not allowed')).toBeInTheDocument();
        expect(listUserIdentityProviders).not.toHaveBeenCalled();
    });

    it.each(['profile', 'history'])('shows the not allowed panel in place of the %s tab without the read permission', async tab => {
        renderAt(`${usersPath}/u-1/${tab}`, ['domain_user_list']);

        expect(await screen.findByText('Not allowed')).toBeInTheDocument();
        expect(getUser).not.toHaveBeenCalled();
    });
});
