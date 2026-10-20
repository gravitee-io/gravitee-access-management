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
import { Navigate, Route } from 'react-router-dom';
import { lazyPage } from '../../lib/routing/lazyPage';
import { RequirePermission } from '../../lib/session/permissions';

export const identityProviderRoutes = (
    <Route path="providers" element={<RequirePermission anyOf={['domain_identity_provider_list']} />}>
        <Route index element={lazyPage(() => import('./IdentityProvidersPage'), 'IdentityProvidersPage')} />
        <Route path="new" element={lazyPage(() => import('./NewIdentityProviderPage'), 'NewIdentityProviderPage')} />
        <Route path=":providerId" element={lazyPage(() => import('./IdentityProviderLayout'), 'IdentityProviderLayout')}>
            <Route index element={<Navigate to="settings" replace />} />
            <Route path="settings" element={lazyPage(() => import('./IdentityProviderSettingsPage'), 'IdentityProviderSettingsPage')} />
            <Route path="mappers" element={lazyPage(() => import('./IdentityProviderMappersPage'), 'IdentityProviderMappersPage')} />
            <Route path="roles" element={lazyPage(() => import('./IdentityProviderRolesPage'), 'IdentityProviderRolesPage')} />
            <Route path="groups" element={lazyPage(() => import('./IdentityProviderGroupsPage'), 'IdentityProviderGroupsPage')} />
        </Route>
    </Route>
);
