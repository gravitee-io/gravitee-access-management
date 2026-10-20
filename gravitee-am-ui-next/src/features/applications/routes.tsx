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
import { APPLICATION_NAV_GROUPS, navItems } from '../../app/navigation';
import { lazyPage } from '../../lib/routing/lazyPage';
import { RequirePermission } from '../../lib/session/permissions';

/** Application sections the console has built. Every other section gets a placeholder. */
const BUILT_SECTIONS = new Set(['overview', 'design/flows', 'settings/general']);

export const applicationRoutes = (
    <>
        <Route path="applications" element={lazyPage(() => import('./ApplicationsPage'), 'ApplicationsPage')} />
        <Route path="applications/:applicationId" element={lazyPage(() => import('./ApplicationPage'), 'ApplicationLayout')}>
            <Route index element={<Navigate to="overview" replace />} />
            <Route path="overview" element={lazyPage(() => import('./ApplicationPage'), 'ApplicationOverviewPage')} />
            <Route
                path="design/flows"
                element={
                    <RequirePermission anyOf={['application_flow_list', 'application_flow_read']}>
                        {lazyPage(() => import('./ApplicationPage'), 'ApplicationFlowsPage')}
                    </RequirePermission>
                }
            />
            <Route
                path="settings/general"
                element={
                    <RequirePermission anyOf={['application_settings_read']}>
                        {lazyPage(() => import('./ApplicationPage'), 'ApplicationGeneralPage')}
                    </RequirePermission>
                }
            />
            {navItems(APPLICATION_NAV_GROUPS)
                .filter(item => !BUILT_SECTIONS.has(item.key))
                .map(item => (
                    <Route key={item.key} path={item.key} element={<PlaceholderPage title={item.title} />} />
                ))}
        </Route>
    </>
);
