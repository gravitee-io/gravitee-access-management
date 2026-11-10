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
import { toast } from '@gravitee/graphene-core';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route } from 'react-router-dom';
import { type Application, patchApplication } from '../../../lib/api/applications';
import { listIdentityProviderPlugins, listIdentityProviders } from '../../../lib/api/identity-providers';
import { ApplicationContext } from '../ApplicationContext';
import { ApplicationIdentityProvidersPage } from '../ApplicationIdentityProvidersPage';
import { applicationsPath, currentDomain, ref, renderRoutes, webApplication } from './render';

vi.mock('../../../lib/api/applications', async importOriginal => ({ ...(await importOriginal<object>()), patchApplication: vi.fn() }));
vi.mock('../../../lib/api/identity-providers', () => ({ listIdentityProviders: vi.fn(), listIdentityProviderPlugins: vi.fn() }));
vi.mock('../../../lib/api/management-api', () => ({ getSpelGrammar: vi.fn().mockResolvedValue({}) }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));
vi.mock('@gravitee/graphene-core/code-editor', () => ({
    CodeEditor: ({ value, onChange, readOnly }: { value: string; onChange: (value: string) => void; readOnly: boolean }) => (
        <textarea aria-label="Rule" value={value} readOnly={readOnly} onChange={event => onChange(event.target.value)} />
    ),
}));
vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));

const pagePath = `${applicationsPath}/app-1/idp`;
const UPDATE = ['application_identity_provider_list', 'application_identity_provider_read', 'application_identity_provider_update'];

const application: Application = {
    ...webApplication,
    identityProviders: [
        { identity: 'inline', priority: 0, selectionRule: "{#request.params['username'] matches '.+@acme.com$'}" },
        { identity: 'google', priority: 0 },
    ],
};

function renderPage(permissions = UPDATE, app = application) {
    return renderRoutes(
        pagePath,
        <Route
            path={pagePath}
            element={
                <ApplicationContext value={{ application: app, applicationPath: `${applicationsPath}/app-1`, listPath: applicationsPath }}>
                    <ApplicationIdentityProvidersPage />
                </ApplicationContext>
            }
        />,
        permissions,
    );
}

function rowNames(table: string) {
    return within(screen.getByRole('table', { name: table }))
        .getAllByRole('row')
        .slice(1)
        .map(row => within(row).getAllByRole('cell')[1]?.textContent);
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listIdentityProviders).mockResolvedValue([
        { id: 'ldap', name: 'LDAP', type: 'ldap-am-idp', system: false, external: false },
        { id: 'inline', name: 'Inline', type: 'inline-am-idp', system: false, external: false },
        { id: 'custom', name: 'Corporate', type: 'corp-am-idp', system: false, external: false },
        { id: 'google', name: 'Google', type: 'google-am-idp', system: false, external: true },
    ]);
    vi.mocked(listIdentityProviderPlugins).mockResolvedValue([
        { id: 'ldap-am-idp', name: 'LDAP', displayName: 'LDAP / AD', external: false },
        { id: 'inline-am-idp', name: 'Inline', external: false },
        { id: 'google-am-idp', name: 'Google', external: true },
    ]);
    vi.mocked(patchApplication).mockImplementation(async (_ref, _id, patch) => ({ ...application, ...patch }) as Application);
});

describe('ApplicationIdentityProvidersPage', () => {
    it('lists the selected providers first, by priority, in two lists', async () => {
        renderPage();

        await screen.findByRole('table', { name: 'Identity providers' });
        expect(rowNames('Identity providers')).toEqual(['Inline', 'LDAP', 'Corporate']);
        expect(rowNames('Social and enterprise providers')).toEqual(['Google']);
        expect(screen.getByText('LDAP / AD')).toBeInTheDocument();
        expect(screen.getByText('Custom')).toBeInTheDocument();
        expect(screen.getByRole('switch', { name: 'Use Inline' })).toBeChecked();
        expect(screen.getByRole('switch', { name: 'Use LDAP' })).not.toBeChecked();
        expect(listIdentityProviders).toHaveBeenCalledWith(ref);
    });

    it('saves the selected providers with their priority in each list', async () => {
        renderPage();

        await userEvent.click(await screen.findByRole('switch', { name: 'Use LDAP' }));
        await userEvent.click(screen.getByRole('button', { name: 'Move LDAP up' }));
        await userEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchApplication).toHaveBeenCalledWith(ref, 'app-1', {
                identityProviders: [
                    { identity: 'ldap', selectionRule: '', priority: 0 },
                    { identity: 'inline', selectionRule: "{#request.params['username'] matches '.+@acme.com$'}", priority: 1 },
                    { identity: 'google', selectionRule: '', priority: 0 },
                ],
            }),
        );
        expect(toast.success).toHaveBeenCalledWith('Application updated');
        expect(rowNames('Identity providers')).toEqual(['LDAP', 'Inline', 'Corporate']);
        await waitFor(() => expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument());
    });

    it('offers Save only after a change, and Discard restores the lists', async () => {
        renderPage();

        await screen.findByRole('table', { name: 'Identity providers' });
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Move Corporate up' }));
        expect(rowNames('Identity providers')).toEqual(['Inline', 'Corporate', 'LDAP']);
        await userEvent.click(screen.getByRole('button', { name: 'Discard' }));

        expect(rowNames('Identity providers')).toEqual(['Inline', 'LDAP', 'Corporate']);
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });

    it('reorders a provider by drag and drop, and announces its new priority', async () => {
        renderPage();

        const table = await screen.findByRole('table', { name: 'Identity providers' });
        const [, inline, , corporate] = within(table).getAllByRole('row');
        fireEvent.dragStart(corporate!, { dataTransfer: { effectAllowed: '' } });
        fireEvent.dragOver(inline!);
        fireEvent.drop(inline!);

        expect(rowNames('Identity providers')).toEqual(['Corporate', 'Inline', 'LDAP']);
        expect(screen.getByText('Corporate moved to priority 1 of 3')).toBeInTheDocument();
    });

    it('opens a new selection rule with the default of the provider type', async () => {
        renderPage();

        await userEvent.click(await screen.findByRole('button', { name: 'New selection rule of Google' }));
        const dialog = screen.getByRole('dialog', { name: 'Selection rule — Google' });

        expect(within(dialog).getByRole('textbox', { name: 'Rule' })).toHaveValue("{#request.params['username'] matches '.+@gmail.com$'}");
        expect(within(dialog).getByRole('group', { name: 'Rule' })).toBeInTheDocument();
    });

    it('selects the provider when its rule is applied', async () => {
        renderPage();

        await userEvent.click(await screen.findByRole('button', { name: 'New selection rule of LDAP' }));
        const dialog = screen.getByRole('dialog', { name: 'Selection rule — LDAP' });
        const rule = within(dialog).getByRole('textbox', { name: 'Rule' });
        await userEvent.clear(rule);
        await userEvent.paste("{#request.params['username'] matches '.+@corp.com$'}");
        await userEvent.click(within(dialog).getByRole('button', { name: 'Apply' }));

        expect(screen.getByRole('switch', { name: 'Use LDAP' })).toBeChecked();
        await userEvent.click(screen.getByRole('button', { name: 'Save' }));
        await waitFor(() =>
            expect(patchApplication).toHaveBeenCalledWith(ref, 'app-1', {
                identityProviders: [
                    { identity: 'inline', selectionRule: "{#request.params['username'] matches '.+@acme.com$'}", priority: 0 },
                    { identity: 'ldap', selectionRule: "{#request.params['username'] matches '.+@corp.com$'}", priority: 1 },
                    { identity: 'google', selectionRule: '', priority: 0 },
                ],
            }),
        );
    });

    it('is read-only without the update permission', async () => {
        renderPage(['application_identity_provider_list']);

        await screen.findByRole('table', { name: 'Identity providers' });
        expect(screen.queryByRole('button', { name: /^Move / })).not.toBeInTheDocument();
        expect(screen.getByRole('switch', { name: 'Use LDAP' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: /New selection rule/ })).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Selection rule of Inline' }));

        const dialog = screen.getByRole('dialog');
        expect(within(dialog).getByRole('textbox', { name: 'Rule' })).toHaveAttribute('readonly');
        expect(within(dialog).queryByRole('button', { name: 'Apply' })).not.toBeInTheDocument();
        expect(within(dialog).queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument();
    });

    it('is read-only when the user cannot read the linked providers', async () => {
        renderPage(['application_identity_provider_list', 'application_identity_provider_update']);

        await screen.findByRole('table', { name: 'Identity providers' });
        expect(screen.getByRole('switch', { name: 'Use LDAP' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: /^Move / })).not.toBeInTheDocument();
    });

    it('shows the empty state when the domain has no identity provider', async () => {
        vi.mocked(listIdentityProviders).mockResolvedValue([]);
        renderPage();

        expect(await screen.findByText('No identity providers')).toBeInTheDocument();
        expect(screen.queryByRole('table')).not.toBeInTheDocument();
    });
});
