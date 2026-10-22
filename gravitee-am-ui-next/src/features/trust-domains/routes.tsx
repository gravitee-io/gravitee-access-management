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
import { Navigate, Outlet, Route } from 'react-router-dom';
import { lazyPage } from '../../lib/routing/lazyPage';
import { RequirePermission } from '../../lib/session/permissions';

/** The Angular `TrustDomainsContainerComponent` route and its children. Each page draws the container's tabs itself. */
export const trustDomainRoutes = (
    <Route
        path="trust-domains"
        element={
            <RequirePermission anyOf={['domain_trust_domain_list']}>
                <Outlet />
            </RequirePermission>
        }
    >
        <Route index element={<Navigate to="domains" replace />} />
        <Route path="domains" element={lazyPage(() => import('./TrustDomainsPage'), 'TrustDomainsPage')} />
        <Route
            path="key-retrieval"
            element={
                <RequirePermission anyOf={['domain_settings_read']}>
                    {lazyPage(() => import('./TrustDomainKeyRetrievalPage'), 'TrustDomainKeyRetrievalPage')}
                </RequirePermission>
            }
        />
        <Route
            path="domains/new"
            element={
                <RequirePermission anyOf={['domain_trust_domain_create']}>
                    {lazyPage(() => import('./NewTrustDomainPage'), 'NewTrustDomainPage')}
                </RequirePermission>
            }
        />
        <Route
            path="domains/:trustDomainId"
            element={
                <RequirePermission anyOf={['domain_trust_domain_read']}>
                    {lazyPage(() => import('./TrustDomainPage'), 'TrustDomainPage')}
                </RequirePermission>
            }
        />
    </Route>
);
