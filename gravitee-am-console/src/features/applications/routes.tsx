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
import type { ApplicationType } from '../../lib/api/applications';
import { lazyPage } from '../../lib/routing/lazyPage';
import { RequirePermission } from '../../lib/session/permissions';
import { RequireApplicationSection } from './RequireApplicationSection';
import { RequireApplicationType } from './RequireApplicationType';

const USER_FACING: readonly ApplicationType[] = ['web', 'native', 'browser', 'resource_server'];

/** The sections later stories port. Each shows a placeholder until then. */
const PLACEHOLDERS: readonly { readonly path: string; readonly title: string }[] = [
    { path: 'design/forms', title: 'Forms' },
    { path: 'design/emails', title: 'Emails' },
    { path: 'design/flows', title: 'Flows' },
    { path: 'settings/secrets-certificates', title: 'Secrets & Certificates' },
    { path: 'settings/oauth2', title: 'OAuth 2.0 / OIDC' },
    { path: 'settings/saml2', title: 'SAML 2.0' },
    { path: 'settings/login', title: 'Login' },
    { path: 'settings/factors', title: 'Multifactor Auth' },
    { path: 'settings/account', title: 'User Accounts' },
    { path: 'settings/session', title: 'Session management' },
    { path: 'settings/resources', title: 'Resources' },
];

export const applicationRoutes = (
    <Route path="applications">
        <Route element={<RequirePermission anyOf={['application_list']} />}>
            <Route index element={lazyPage(() => import('./ApplicationsPage'), 'ApplicationsPage')} />
        </Route>
        <Route element={<RequirePermission anyOf={['application_create']} />}>
            <Route path="new" element={lazyPage(() => import('./CreateApplicationPage'), 'CreateApplicationPage')} />
        </Route>
        <Route path=":appId" element={lazyPage(() => import('./ApplicationLayout'), 'ApplicationLayout')}>
            <Route index element={<Navigate to="overview" replace />} />
            <Route path="overview" element={lazyPage(() => import('./ApplicationOverviewPage'), 'ApplicationOverviewPage')} />
            <Route path="endpoints" element={lazyPage(() => import('./ApplicationEndpointsPage'), 'ApplicationEndpointsPage')} />
            <Route element={<RequirePermission anyOf={['application_identity_provider_list']} />}>
                <Route element={<RequireApplicationType types={[...USER_FACING, 'agent']} />}>
                    <Route
                        path="idp"
                        element={lazyPage(() => import('./ApplicationIdentityProvidersPage'), 'ApplicationIdentityProvidersPage')}
                    />
                </Route>
            </Route>
            <Route element={<RequirePermission anyOf={['application_analytics_list']} />}>
                <Route element={<RequireApplicationType types={USER_FACING} />}>
                    <Route path="analytics" element={lazyPage(() => import('./ApplicationAnalyticsPage'), 'ApplicationAnalyticsPage')} />
                </Route>
            </Route>
            <Route path="settings" element={lazyPage(() => import('./ApplicationSettingsLayout'), 'ApplicationSettingsLayout')}>
                <Route element={<RequirePermission anyOf={['application_settings_read']} />}>
                    <Route path="general" element={lazyPage(() => import('./ApplicationGeneralPage'), 'ApplicationGeneralPage')} />
                    <Route element={<RequireApplicationType types={['agent']} />}>
                        <Route path="agent" element={lazyPage(() => import('./ApplicationAgentPage'), 'ApplicationAgentPage')} />
                    </Route>
                    <Route path="metadata" element={lazyPage(() => import('./ApplicationMetadataPage'), 'ApplicationMetadataPage')} />
                </Route>
                <Route element={<RequirePermission anyOf={['application_member_list']} />}>
                    <Route path="members" element={lazyPage(() => import('./ApplicationMembersPage'), 'ApplicationMembersPage')} />
                </Route>
            </Route>
            {PLACEHOLDERS.map(({ path, title }) => (
                <Route key={path} element={<RequireApplicationSection section={path} />}>
                    <Route path={path} element={<PlaceholderPage title={title} />} />
                </Route>
            ))}
        </Route>
    </Route>
);
