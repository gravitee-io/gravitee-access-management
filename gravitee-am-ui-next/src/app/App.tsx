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
import { LayoutSlotsProvider, Toaster, TooltipProvider } from '@gravitee/graphene-core';
import { QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import {
    ApplicationFlowsPage,
    ApplicationGeneralPage,
    ApplicationLayout,
    ApplicationOverviewPage,
} from '../features/applications/ApplicationPage';
import { ApplicationsPage } from '../features/applications/ApplicationsPage';
import { LoginCallback, LoginRedirect, LogoutCallback, LogoutRedirect } from '../features/auth/AuthRoutes';
import { DomainsPage } from '../features/domains/DomainsPage';
import { FlowsPage } from '../features/flows/FlowsPage';
import { LoginFormPage } from '../features/forms/LoginFormPage';
import { IdentityProviderPage } from '../features/identity-providers/IdentityProviderPage';
import { IdentityProvidersPage } from '../features/identity-providers/IdentityProvidersPage';
import { OrganizationLayout } from '../features/organization/OrganizationLayout';
import { DomainSettingsLayout } from '../features/settings/DomainSettingsLayout';
import { redirectToLogin } from '../lib/api/auth';
import { HttpError } from '../lib/api/http';
import { DomainScope } from '../lib/session/domain';
import { EnvironmentScope, RootRedirect } from '../lib/session/environment';
import { SessionGate } from '../lib/session/session';
import { Shell } from './Shell';
import { DomainPlaceholderPage, PlaceholderPage } from './components/PlaceholderPage';
import { APPLICATION_NAV_GROUPS, DOMAIN_SETTINGS_NAV_GROUPS, navItems, ORGANIZATION_NAV_GROUPS } from './navigation';

/** Domain settings the spike has built. Every other settings item gets a placeholder. */
const BUILT_DOMAIN_SETTINGS = new Set(['providers', 'forms', 'flows']);
const BUILT_APPLICATION_SECTIONS = new Set(['overview', 'design/flows', 'settings/general']);

const queryClient = new QueryClient({
    queryCache: new QueryCache({
        onError: error => {
            if (error instanceof HttpError && error.status === 401) {
                redirectToLogin();
            }
        },
    }),
    defaultOptions: {
        queries: {
            retry: (failureCount, error) => !(error instanceof HttpError && error.status < 500) && failureCount < 1,
            refetchOnWindowFocus: false,
        },
    },
});

export function App() {
    return (
        <QueryClientProvider client={queryClient}>
            <TooltipProvider>
                <LayoutSlotsProvider>
                    <BrowserRouter>
                        <Routes>
                            <Route path="/login" element={<LoginRedirect />} />
                            <Route path="/login/callback" element={<LoginCallback />} />
                            <Route path="/logout" element={<LogoutRedirect />} />
                            <Route path="/logout/callback" element={<LogoutCallback />} />
                            <Route
                                path="*"
                                element={
                                    <SessionGate>
                                        <Routes>
                                            <Route path="/environments/:envHrid" element={<EnvironmentScope />}>
                                                <Route element={<Shell />}>
                                                    <Route index element={<Navigate to="domains" replace />} />
                                                    <Route path="domains" element={<DomainsPage />} />
                                                    <Route path="domains/:domainHrid" element={<DomainScope />}>
                                                        <Route index element={<Navigate to="dashboard" replace />} />
                                                        <Route path="dashboard" element={<DomainPlaceholderPage title="Dashboard" />} />
                                                        <Route path="applications" element={<ApplicationsPage />} />
                                                        <Route path="applications/:applicationId" element={<ApplicationLayout />}>
                                                            <Route index element={<Navigate to="overview" replace />} />
                                                            <Route path="overview" element={<ApplicationOverviewPage />} />
                                                            <Route path="design/flows" element={<ApplicationFlowsPage />} />
                                                            <Route path="settings/general" element={<ApplicationGeneralPage />} />
                                                            {navItems(APPLICATION_NAV_GROUPS)
                                                                .filter(item => !BUILT_APPLICATION_SECTIONS.has(item.key))
                                                                .map(item => (
                                                                    <Route
                                                                        key={item.key}
                                                                        path={item.key}
                                                                        element={<PlaceholderPage title={item.title} />}
                                                                    />
                                                                ))}
                                                        </Route>
                                                        <Route path="agents" element={<DomainPlaceholderPage title="Agents" />} />
                                                        <Route path="mcp-servers" element={<DomainPlaceholderPage title="MCP Servers" />} />
                                                        <Route
                                                            path="authorization"
                                                            element={<DomainPlaceholderPage title="Authorization" />}
                                                        />
                                                        <Route path="alerts" element={<DomainPlaceholderPage title="Alerts" />} />
                                                        <Route path="settings" element={<DomainSettingsLayout />}>
                                                            <Route index element={<Navigate to="general" replace />} />
                                                            <Route path="providers" element={<IdentityProvidersPage />} />
                                                            <Route
                                                                path="providers/:identityProviderId"
                                                                element={<IdentityProviderPage />}
                                                            />
                                                            <Route path="forms" element={<LoginFormPage />} />
                                                            <Route path="flows" element={<FlowsPage />} />
                                                            {navItems(DOMAIN_SETTINGS_NAV_GROUPS)
                                                                .filter(item => !BUILT_DOMAIN_SETTINGS.has(item.key))
                                                                .map(item => (
                                                                    <Route
                                                                        key={item.key}
                                                                        path={item.key}
                                                                        element={<PlaceholderPage title={item.title} />}
                                                                    />
                                                                ))}
                                                        </Route>
                                                    </Route>
                                                    <Route path="organization" element={<OrganizationLayout />}>
                                                        <Route index element={<Navigate to="general" replace />} />
                                                        {navItems(ORGANIZATION_NAV_GROUPS).map(item => (
                                                            <Route
                                                                key={item.key}
                                                                path={item.key}
                                                                element={<PlaceholderPage title={item.title} />}
                                                            />
                                                        ))}
                                                    </Route>
                                                </Route>
                                            </Route>
                                            <Route path="*" element={<RootRedirect />} />
                                        </Routes>
                                    </SessionGate>
                                }
                            />
                        </Routes>
                    </BrowserRouter>
                </LayoutSlotsProvider>
                <Toaster position="bottom-right" richColors closeButton />
            </TooltipProvider>
        </QueryClientProvider>
    );
}
