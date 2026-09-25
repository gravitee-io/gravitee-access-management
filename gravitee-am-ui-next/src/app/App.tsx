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
import { ApplicationPage } from '../features/applications/ApplicationPage';
import { ApplicationsPage } from '../features/applications/ApplicationsPage';
import { LoginCallback, LoginRedirect, LogoutCallback, LogoutRedirect } from '../features/auth/AuthRoutes';
import { DomainsPage } from '../features/domains/DomainsPage';
import { LoginFormPage } from '../features/forms/LoginFormPage';
import { IdentityProviderPage } from '../features/identity-providers/IdentityProviderPage';
import { IdentityProvidersPage } from '../features/identity-providers/IdentityProvidersPage';
import { redirectToLogin } from '../lib/api/auth';
import { HttpError } from '../lib/api/http';
import { DomainScope } from '../lib/session/domain';
import { EnvironmentScope, RootRedirect } from '../lib/session/environment';
import { SessionGate } from '../lib/session/session';
import { Shell } from './Shell';

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
                                                        <Route index element={<Navigate to="applications" replace />} />
                                                        <Route path="applications" element={<ApplicationsPage />} />
                                                        <Route path="applications/:applicationId" element={<ApplicationPage />} />
                                                        <Route path="identity-providers" element={<IdentityProvidersPage />} />
                                                        <Route
                                                            path="identity-providers/:identityProviderId"
                                                            element={<IdentityProviderPage />}
                                                        />
                                                        <Route path="login-form" element={<LoginFormPage />} />
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
