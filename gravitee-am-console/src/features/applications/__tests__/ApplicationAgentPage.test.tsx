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
import type { Application } from '../../../lib/api/applications';
import { ApplicationAgentPage } from '../ApplicationAgentPage';
import { ApplicationContext } from '../ApplicationContext';
import { applicationsPath, renderRoutes } from './render';

const appPath = `${applicationsPath}/agent-1`;

function renderPage(application: Application) {
    renderRoutes(
        `${appPath}/settings/agent`,
        <Route
            path={`${appPath}/settings/agent`}
            element={
                <ApplicationContext value={{ application, applicationPath: appPath, listPath: applicationsPath }}>
                    <ApplicationAgentPage />
                </ApplicationContext>
            }
        />,
        ['application_settings_read'],
    );
}

describe('ApplicationAgentPage', () => {
    it('shows the agent type, read-only', () => {
        renderPage({ id: 'agent-1', name: 'Helper', type: 'agent', kind: 'HOSTED_DELEGATED' });

        const type = screen.getByRole('textbox', { name: 'Agent type' });
        expect(type).toHaveValue('Hosted delegated');
        expect(type).toHaveAttribute('readonly');
        expect(screen.getByRole('link', { name: 'OAuth 2.0 / OIDC' })).toHaveAttribute('href', `${appPath}/settings/oauth2`);
    });

    it('shows a kind it does not know as it is', () => {
        renderPage({ id: 'agent-1', name: 'Helper', type: 'agent', kind: 'FUTURE' as Application['kind'] });

        expect(screen.getByRole('textbox', { name: 'Agent type' })).toHaveValue('FUTURE');
    });
});
