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
import { ORGANIZATION_SETTINGS_NAV_GROUPS } from '../../app/navigation';
import { lazyPage } from '../../lib/routing/lazyPage';
import { RequirePermission } from '../../lib/session/permissions';
import { OrganizationLayout } from './OrganizationLayout';
import { RequireSelfManaged } from './RequireSelfManaged';

const BUILT_PAGES = new Set(['general', 'forms', 'tags', 'entrypoints', 'cockpit']);

export const organizationRoutes = (
    <Route
        path="organization"
        element={
            <RequirePermission anyOf={['organization_settings_read']}>
                <OrganizationLayout />
            </RequirePermission>
        }
    >
        <Route index element={<Navigate to="general" replace />} />
        <Route
            path="general"
            element={
                <RequirePermission anyOf={['organization_settings_read']}>
                    {lazyPage(() => import('./OrganizationGeneralPage'), 'OrganizationGeneralPage')}
                </RequirePermission>
            }
        />
        <Route path="forms" element={<RequirePermission anyOf={['organization_form_list']} />}>
            <Route index element={lazyPage(() => import('./OrganizationFormsPage'), 'OrganizationFormsPage')} />
            <Route
                path="form"
                element={
                    <RequirePermission anyOf={['organization_form_read']}>
                        {lazyPage(() => import('./OrganizationFormPage'), 'OrganizationFormPage')}
                    </RequirePermission>
                }
            />
        </Route>
        <Route path="tags" element={<RequirePermission anyOf={['organization_tag_list']} />}>
            <Route index element={lazyPage(() => import('./TagsPage'), 'TagsPage')} />
            <Route
                path="new"
                element={
                    <RequirePermission anyOf={['organization_tag_create']}>
                        {lazyPage(() => import('./NewTagPage'), 'NewTagPage')}
                    </RequirePermission>
                }
            />
            <Route
                path=":tagId"
                element={
                    <RequirePermission anyOf={['organization_tag_read']}>
                        {lazyPage(() => import('./TagPage'), 'TagPage')}
                    </RequirePermission>
                }
            />
        </Route>
        <Route path="entrypoints" element={<RequirePermission anyOf={['organization_entrypoint_list']} />}>
            <Route index element={lazyPage(() => import('./OrganizationEntrypointsPage'), 'OrganizationEntrypointsPage')} />
            <Route
                path="new"
                element={
                    <RequirePermission anyOf={['organization_entrypoint_create']}>
                        {lazyPage(() => import('./NewOrganizationEntrypointPage'), 'NewOrganizationEntrypointPage')}
                    </RequirePermission>
                }
            />
            <Route
                path=":entrypointId"
                element={
                    <RequirePermission anyOf={['organization_entrypoint_read']}>
                        {lazyPage(() => import('./OrganizationEntrypointPage'), 'OrganizationEntrypointPage')}
                    </RequirePermission>
                }
            />
        </Route>
        <Route
            path="cockpit"
            element={
                <RequirePermission anyOf={['installation_read']}>
                    <RequireSelfManaged>{lazyPage(() => import('./CockpitPage'), 'CockpitPage')}</RequireSelfManaged>
                </RequirePermission>
            }
        />
        {ORGANIZATION_SETTINGS_NAV_GROUPS.flatMap(group => group.items)
            .filter(item => !BUILT_PAGES.has(item.key))
            .map(item => (
                <Route key={item.key} path={`${item.key}/*`} element={<PlaceholderPage title={item.title} />} />
            ))}
    </Route>
);
