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
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom';
import { Can, LoadPermissionScope, PermissionScope, RequirePermission, usePermissions } from '../permissions';

function Probe({ names }: { readonly names: string[] }) {
    const { has, hasAny } = usePermissions();
    return (
        <p>
            has:{String(has(...names))} any:{String(hasAny(...names))}
        </p>
    );
}

describe('usePermissions', () => {
    it('unions the lists of the nested scopes, all-of for has and any-of for hasAny', () => {
        render(
            <PermissionScope permissions={['organization_settings_read']}>
                <PermissionScope permissions={['domain_list', 'application_list']}>
                    <Probe names={['domain_list', 'organization_settings_read']} />
                    <Probe names={['domain_list', 'application_create']} />
                    <Probe names={['application_create']} />
                </PermissionScope>
            </PermissionScope>,
        );

        expect(screen.getByText('has:true any:true')).toBeInTheDocument();
        expect(screen.getByText('has:false any:true')).toBeInTheDocument();
        expect(screen.getByText('has:false any:false')).toBeInTheDocument();
    });
});

describe('Can', () => {
    it('renders the children only when the user holds the permissions', () => {
        render(
            <PermissionScope permissions={['application_settings_read']}>
                <Can all={['application_settings_read']}>
                    <button>Read</button>
                </Can>
                <Can all={['application_settings_update']} fallback={<span>read only</span>}>
                    <button>Save</button>
                </Can>
                <Can any={['application_settings_delete', 'application_settings_read']}>
                    <button>Any</button>
                </Can>
            </PermissionScope>,
        );

        expect(screen.getByRole('button', { name: 'Read' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.getByText('read only')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Any' })).toBeInTheDocument();
    });
});

describe('RequirePermission', () => {
    it('renders the child routes when one permission matches, else the forbidden panel', () => {
        render(
            <PermissionScope permissions={['domain_list']}>
                <MemoryRouter initialEntries={['/allowed']}>
                    <Routes>
                        <Route element={<RequirePermission anyOf={['application_list', 'domain_list']} />}>
                            <Route path="allowed" element={<p>Allowed page</p>} />
                        </Route>
                    </Routes>
                </MemoryRouter>
                <RequirePermission anyOf={['domain_create']}>
                    <p>Create page</p>
                </RequirePermission>
            </PermissionScope>,
        );

        expect(screen.getByText('Allowed page')).toBeInTheDocument();
        expect(screen.queryByText('Create page')).not.toBeInTheDocument();
        expect(screen.getByText('Not allowed')).toBeInTheDocument();
    });
});

describe('LoadPermissionScope', () => {
    it('loads the list before it renders the children', async () => {
        const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
        const queryFn = vi.fn().mockResolvedValue(['domain_settings_update']);
        render(
            <QueryClientProvider client={client}>
                <LoadPermissionScope queryKey={['domain-permissions', 'd1']} queryFn={queryFn}>
                    <Probe names={['domain_settings_update']} />
                    <Outlet />
                </LoadPermissionScope>
            </QueryClientProvider>,
        );

        expect(screen.queryByText('has:true any:true')).not.toBeInTheDocument();
        expect(await screen.findByText('has:true any:true')).toBeInTheDocument();
        expect(queryFn).toHaveBeenCalledOnce();
    });
});
