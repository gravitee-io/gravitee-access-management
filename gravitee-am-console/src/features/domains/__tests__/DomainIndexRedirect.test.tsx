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
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom';
import { PermissionScope } from '../../../lib/session/permissions';
import { DomainIndexRedirect } from '../DomainIndexRedirect';

function Section() {
    return <p>Opened {useParams().section}</p>;
}

describe('DomainIndexRedirect', () => {
    it.each([
        [['domain_analytics_read', 'application_list'], 'dashboard'],
        [['domain_authorization_engine_list', 'application_list'], 'authorization'],
        [['application_list', 'protected_resource_list'], 'applications'],
        [['protected_resource_list'], 'mcp-servers'],
        [[], 'settings'],
    ])('with %j opens %s, as the Angular DomainComponent', async (permissions, section) => {
        render(
            <PermissionScope permissions={permissions}>
                <MemoryRouter initialEntries={['/domains/acme']}>
                    <Routes>
                        <Route path="/domains/acme">
                            <Route index element={<DomainIndexRedirect />} />
                            <Route path=":section" element={<Section />} />
                        </Route>
                    </Routes>
                </MemoryRouter>
            </PermissionScope>,
        );

        expect(await screen.findByText(`Opened ${section}`)).toBeInTheDocument();
    });
});
