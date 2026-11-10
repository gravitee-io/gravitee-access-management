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
import { listDomainEntrypoints } from '../../../lib/api/domains';
import { ApplicationContext } from '../ApplicationContext';
import { ApplicationOverviewPage } from '../ApplicationOverviewPage';
import { applicationsPath, currentDomain, renderRoutes, webApplication } from './render';

vi.mock('../../../lib/api/domains', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    listDomainEntrypoints: vi.fn(),
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

const appPath = `${applicationsPath}/app-1`;
const BASE = 'https://auth.acme.com/acme';

function renderPage(application: Application, permissions = ['application_openid_read']) {
    return renderRoutes(
        `${appPath}/overview`,
        <Route
            path={`${appPath}/overview`}
            element={
                <ApplicationContext value={{ application, applicationPath: appPath, listPath: applicationsPath }}>
                    <ApplicationOverviewPage />
                </ApplicationContext>
            }
        />,
        permissions,
    );
}

function withOAuth(type: Application['type'], oauth: NonNullable<Application['settings']>['oauth']): Application {
    return { ...webApplication, type, settings: { oauth: { clientId: 'portal-client', ...oauth } } };
}

const codeBlocks = () => screen.getAllByText((_, element) => element?.tagName === 'PRE').map(pre => pre.textContent ?? '');

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listDomainEntrypoints).mockResolvedValue([{ id: 'public', url: 'https://auth.acme.com' }]);
});

describe('ApplicationOverviewPage', () => {
    it('writes the login guide with the client id, the first redirect URI and the code flow', async () => {
        renderPage(
            withOAuth('web', {
                redirectUris: ['https://portal.acme/callback?tenant={#context.attributes["t"]}', 'https://other/cb'],
                grantTypes: ['authorization_code'],
            }),
        );

        expect(await screen.findByRole('heading', { name: 'Integrate authentication into your application' })).toBeInTheDocument();
        const blocks = codeBlocks();
        expect(blocks).toContain(
            `${BASE}/oauth/authorize?client_id=portal-client&response_type=code&redirect_uri=${encodeURIComponent('https://portal.acme/callback')}`,
        );
        expect(blocks).toContain('https://portal.acme/callback');
        expect(blocks.find(block => block.includes('grant_type=authorization_code'))).toContain('&client_secret=|client_secret|');
        expect(screen.getByRole('link', { name: 'identity providers' })).toHaveAttribute('href', `${appPath}/idp`);
        expect(screen.getByRole('link', { name: 'design' })).toHaveAttribute('href', `${appPath}/design/forms`);
        expect(screen.getByRole('link', { name: 'settings' })).toHaveAttribute('href', `${appPath}/settings`);
    });

    it('uses the token response type and basic authentication when the settings ask for them', async () => {
        renderPage(withOAuth('browser', { grantTypes: ['implicit'], tokenEndpointAuthMethod: 'client_secret_basic' }));

        await screen.findByRole('heading', { name: 'Integrate authentication into your application' });
        const blocks = codeBlocks();
        expect(blocks.find(block => block.includes('/oauth/authorize'))).toContain('response_type=token&redirect_uri=Not%20defined');
        expect(blocks.find(block => block.includes('grant_type=authorization_code'))).toContain("-u 'portal-client:|client_secret|'");
    });

    it('adds the PKCE challenge and verifier when the application forces PKCE', async () => {
        renderPage(withOAuth('native', { grantTypes: ['authorization_code'], forcePKCE: true }));

        expect(await screen.findByText(/code_challenge_method=S256/)).toBeInTheDocument();
        expect(codeBlocks().find(block => block.includes('grant_type=authorization_code'))).toMatch(/&code_verifier=[A-Za-z0-9_-]+'$/);
    });

    it('hides the OAuth settings without application_openid_read', async () => {
        renderPage(withOAuth('web', { grantTypes: ['authorization_code'], forcePKCE: true }), []);

        await screen.findByRole('heading', { name: 'Integrate authentication into your application' });
        expect(screen.getAllByText('Insufficient permission')).toHaveLength(2);
        const authorize = codeBlocks().find(block => block.includes('/oauth/authorize'));
        expect(authorize).toContain('client_id=Insufficient%20permission&response_type=token');
        expect(authorize).not.toContain('code_challenge');
    });

    it('writes the client credentials guide for a service application', async () => {
        renderPage(withOAuth('service', { tokenEndpointAuthMethod: 'client_secret_post' }));

        expect(await screen.findByRole('heading', { name: 'Machine-to-machine interaction' })).toBeInTheDocument();
        expect(codeBlocks()).toContain(
            `curl -X POST \\\n  ${BASE}/oauth/token \\\n  -H 'Content-Type: application/x-www-form-urlencoded' \\\n  -d 'grant_type=client_credentials&client_id=portal-client&client_secret=|client_secret|'`,
        );
    });

    it('writes the UMA guide for a resource server', async () => {
        renderPage(withOAuth('resource_server', {}));

        expect(await screen.findByRole('heading', { name: 'Control access to your resources' })).toBeInTheDocument();
        expect(codeBlocks().some(block => block.includes(`${BASE}/uma/protection/permission`))).toBe(true);
    });

    it('shows the error panel when the entrypoints cannot be read', async () => {
        vi.mocked(listDomainEntrypoints).mockRejectedValue(new Error('Service unavailable'));
        renderPage(webApplication);

        expect(await screen.findByText('Service unavailable')).toBeInTheDocument();
    });
});
