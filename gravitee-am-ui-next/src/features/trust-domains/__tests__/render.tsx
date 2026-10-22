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
import { LayoutSlotsProvider, TooltipProvider } from '@gravitee/graphene-core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Routes } from 'react-router-dom';
import { PermissionScope } from '../../../lib/session/permissions';

export const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };
export const trustDomainsPath = '/environments/dev/domains/acme/settings/trust-domains';

export const currentDomain = {
    domain: {
        id: 'd1',
        hrid: 'acme',
        name: 'Acme',
        enabled: true,
        keyRetrievalSettings: {
            allowUnsecuredHttpUri: false,
            allowPrivateIpAddress: false,
            fetchTimeoutMs: 5000,
            maxResponseSizeKb: 32,
            cacheTtlSeconds: 300,
            cacheMaxEntries: 50,
        },
    },
    ref,
    basePath: '/environments/dev/domains/acme',
    domainsPath: '/environments/dev/domains',
};

export const admin = [
    'domain_settings_read',
    'domain_settings_update',
    'domain_trust_domain_list',
    'domain_trust_domain_create',
    'domain_trust_domain_read',
    'domain_trust_domain_update',
    'domain_trust_domain_delete',
];

/** Renders the routes at a path, with the permissions given. */
export function renderRoutes(path: string, routes: ReactNode, permissions: string[]) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={client}>
            <PermissionScope permissions={permissions}>
                <MemoryRouter initialEntries={[path]}>
                    <LayoutSlotsProvider>
                        <TooltipProvider>
                            <Routes>{routes}</Routes>
                        </TooltipProvider>
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </PermissionScope>
        </QueryClientProvider>,
    );
}
