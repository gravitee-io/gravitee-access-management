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
import { PlaceholderPage } from '../../app/components/PlaceholderPage';
import { DOMAIN_SETTINGS_NAV_GROUPS, navItems } from '../../app/navigation';
import { lazyPage } from '../../lib/routing/lazyPage';
import { RequirePermission } from '../../lib/session/permissions';
import { certificateRoutes } from '../certificates/routes';
import { factorRoutes } from '../factors/routes';
import { flowRoutes } from '../flows/routes';
import { formRoutes } from '../forms/routes';
import { identityProviderRoutes } from '../identity-providers/routes';
import { DomainSettingsLayout } from './DomainSettingsLayout';

/** Domain settings the console has built. Every other settings item gets a placeholder. */
const BUILT_SETTINGS = new Set(['general', 'providers', 'certificates', 'factors', 'forms', 'flows']);

/** The settings section of a domain. A ported unit mounts its routes here, in place of its placeholder. */
export const domainSettingsRoutes = (
    <Route
        path="settings"
        element={
            <RequirePermission anyOf={['domain_settings_read']}>
                <DomainSettingsLayout />
            </RequirePermission>
        }
    >
        <Route index element={<Navigate to="general" replace />} />
        <Route
            path="general"
            element={
                <RequirePermission anyOf={['domain_settings_read']}>
                    {lazyPage(() => import('./DomainGeneralPage'), 'DomainGeneralPage')}
                </RequirePermission>
            }
        />
        {identityProviderRoutes}
        {certificateRoutes}
        {factorRoutes}
        {formRoutes}
        {flowRoutes}
        {navItems(DOMAIN_SETTINGS_NAV_GROUPS)
            .filter(item => !BUILT_SETTINGS.has(item.key))
            .map(item => (
                <Route key={item.key} path={item.key} element={<PlaceholderPage title={item.title} />} />
            ))}
    </Route>
);
