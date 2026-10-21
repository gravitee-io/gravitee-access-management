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
import { applicationRoutes } from '../features/applications/routes';
import { authRoutes } from '../features/auth/routes';
import { DomainIndexRedirect } from '../features/domains/DomainIndexRedirect';
import { domainDashboardRoute, domainRoutes } from '../features/domains/routes';
import { organizationRoutes } from '../features/organization/routes';
import { domainSettingsRoutes } from '../features/settings/routes';
import { redirectToLogin } from '../lib/api/auth';
import { HttpError } from '../lib/api/http';
import { DomainScope } from '../lib/session/domain';
import { EnvironmentScope, RootRedirect } from '../lib/session/environment';
import { SessionGate } from '../lib/session/session';
import { Shell } from './Shell';
import { DomainPlaceholderPage } from './components/PlaceholderPage';

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

/**
 * The route tree. Each feature area exports its routes from its own `routes.tsx`, with the pages loaded on first
 * navigation. This file mounts the areas under the session, environment and domain scopes.
 */
export function App() {
    return (
        <QueryClientProvider client={queryClient}>
            <TooltipProvider>
                <LayoutSlotsProvider>
                    <BrowserRouter>
                        <Routes>
                            {authRoutes}
                            <Route
                                path="*"
                                element={
                                    <SessionGate>
                                        <Routes>
                                            <Route path="/environments/:envHrid" element={<EnvironmentScope />}>
                                                <Route element={<Shell />}>
                                                    <Route index element={<Navigate to="domains" replace />} />
                                                    {domainRoutes}
                                                    <Route path="domains/:domainHrid" element={<DomainScope />}>
                                                        <Route index element={<DomainIndexRedirect />} />
                                                        {domainDashboardRoute}
                                                        {applicationRoutes}
                                                        <Route path="agents" element={<DomainPlaceholderPage title="Agents" />} />
                                                        <Route path="mcp-servers" element={<DomainPlaceholderPage title="MCP Servers" />} />
                                                        <Route
                                                            path="authorization"
                                                            element={<DomainPlaceholderPage title="Authorization" />}
                                                        />
                                                        <Route path="alerts" element={<DomainPlaceholderPage title="Alerts" />} />
                                                        {domainSettingsRoutes}
                                                    </Route>
                                                    {organizationRoutes}
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
