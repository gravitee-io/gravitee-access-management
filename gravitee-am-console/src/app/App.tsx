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
import { LayoutSlotsProvider, ThemeProvider, Toaster, TooltipProvider } from '@gravitee/graphene-core';
import { QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { authRoutes } from '../features/auth/routes';
import { EnvironmentPage } from '../features/environment/EnvironmentPage';
import { HomePage } from '../features/home/HomePage';
import { kitRoutes } from '../features/kit/routes';
import { NotFoundPage } from '../features/not-found/NotFoundPage';
import { redirectToLogin } from '../lib/api/auth';
import { HttpError } from '../lib/api/http';
import { EnvironmentScope } from '../lib/session/environment';
import { SessionGate } from '../lib/session/session';
import { Shell } from './Shell';
import { PlaceholderPage } from './components/PlaceholderPage';

export function createQueryClient(): QueryClient {
    return new QueryClient({
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
}

const queryClient = createQueryClient();

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
                                            <Route index element={<HomePage />} />
                                            <Route path="/environments/:envHrid" element={<EnvironmentScope />}>
                                                <Route element={<Shell />}>
                                                    <Route index element={<EnvironmentPage />} />
                                                    <Route path="domains" element={<PlaceholderPage title="Security Domains" />} />
                                                    <Route path="new-domain" element={<PlaceholderPage title="New Security Domain" />} />
                                                    <Route path="domains/:domainHrid">
                                                        <Route index element={<Navigate to="dashboard" replace />} />
                                                        <Route path="dashboard" element={<PlaceholderPage title="Dashboard" />} />
                                                        <Route path="applications/*" element={<PlaceholderPage title="Applications" />} />
                                                        <Route path="agents/*" element={<PlaceholderPage title="Agents" />} />
                                                        <Route path="mcp-servers/*" element={<PlaceholderPage title="MCP Servers" />} />
                                                        <Route path="authorization/*" element={<PlaceholderPage title="Authorization" />} />
                                                        <Route path="alerts/*" element={<PlaceholderPage title="Alerts" />} />
                                                        <Route path="settings/*" element={<PlaceholderPage title="Settings" />} />
                                                    </Route>
                                                    <Route path="organization/*" element={<PlaceholderPage title="Organization" />} />
                                                    {kitRoutes}
                                                </Route>
                                            </Route>
                                            <Route
                                                path="*"
                                                element={
                                                    <ThemeProvider defaultMode="system">
                                                        <NotFoundPage />
                                                    </ThemeProvider>
                                                }
                                            />
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
