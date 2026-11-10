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
import { LayoutSlotsProvider, TooltipProvider, useLayoutSlots } from '@gravitee/graphene-core';
import { renderWithGraphene } from '@gravitee/graphene-core/testing';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { MemoryRouter, Routes } from 'react-router-dom';
import { PermissionScope } from '../../../lib/session/permissions';

export const ORG = 'DEFAULT';
export const organizationPath = '/environments/dev/organization';

export const session = {
    user: { org: ORG },
    environments: [
        { id: 'env-1', name: 'Dev', hrids: ['dev'] },
        { id: 'env-2', name: 'Prod', hrids: ['prod'] },
    ],
};

export const currentEnvironment = { id: 'env-1', name: 'Dev', hrids: ['dev'] };

/** The layout banner, where `SaveBar` renders. */
function Banner() {
    return <>{useLayoutSlots().slots.banner}</>;
}

/** Renders the routes at a path, with the permissions given. */
export function renderRoutes(path: string, routes: ReactNode, permissions: string[]) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return renderWithGraphene(
        <QueryClientProvider client={client}>
            <PermissionScope permissions={permissions}>
                <MemoryRouter initialEntries={[path]}>
                    <LayoutSlotsProvider>
                        <Banner />
                        <TooltipProvider>
                            <Routes>{routes}</Routes>
                        </TooltipProvider>
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </PermissionScope>
        </QueryClientProvider>,
    );
}
