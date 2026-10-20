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
import { navItems, ORGANIZATION_NAV_GROUPS } from '../../app/navigation';
import { OrganizationLayout } from './OrganizationLayout';

/** The organization section. A ported unit mounts its routes here, in place of its placeholder. */
export const organizationRoutes = (
    <Route path="organization" element={<OrganizationLayout />}>
        <Route index element={<Navigate to="general" replace />} />
        {navItems(ORGANIZATION_NAV_GROUPS).map(item => (
            <Route key={item.key} path={item.key} element={<PlaceholderPage title={item.title} />} />
        ))}
    </Route>
);
