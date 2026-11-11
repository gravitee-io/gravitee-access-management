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
import { screen } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { MembershipsPanel } from '../../../app/components/MembershipsPanel';
import { ApplicationContext } from '../ApplicationContext';
import { ApplicationMembersPage } from '../ApplicationMembersPage';
import { applicationsPath, currentDomain, renderRoutes, webApplication } from './render';

vi.mock('../../../app/components/MembershipsPanel', () => ({ MembershipsPanel: vi.fn(() => <p>Members panel</p>) }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

const appPath = `${applicationsPath}/app-1`;

function renderPage(permissions: string[]) {
    renderRoutes(
        `${appPath}/settings/members`,
        <Route
            path={`${appPath}/settings/members`}
            element={
                <ApplicationContext value={{ application: webApplication, applicationPath: appPath, listPath: applicationsPath }}>
                    <ApplicationMembersPage />
                </ApplicationContext>
            }
        />,
        permissions,
    );
}

function panelProps() {
    return vi.mocked(MembershipsPanel).mock.calls[0]?.[0];
}

beforeEach(() => {
    vi.clearAllMocks();
});

describe('ApplicationMembersPage', () => {
    it('shows the application members with the application roles', () => {
        renderPage(['application_member_list']);

        expect(screen.getByText('Members panel')).toBeInTheDocument();
        expect(panelProps()).toEqual({
            membersPath: '/organizations/DEFAULT/environments/env-1/domains/d1/applications/app-1/members',
            roleType: 'APPLICATION',
            resourceLabel: 'application',
            canCreate: false,
            canUpdate: false,
            canDelete: false,
        });
    });

    it('passes each member permission on its own', () => {
        renderPage(['application_member_list', 'application_member_create', 'application_member_delete']);

        expect(panelProps()).toMatchObject({ canCreate: true, canUpdate: false, canDelete: true });
    });
});
