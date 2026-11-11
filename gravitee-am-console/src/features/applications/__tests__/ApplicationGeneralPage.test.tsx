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
import { selectHarness } from '@gravitee/graphene-core/testing';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { type Application, deleteApplication, patchApplication, updateApplicationType } from '../../../lib/api/applications';
import { ApplicationContext } from '../ApplicationContext';
import { ApplicationGeneralPage } from '../ApplicationGeneralPage';
import { applicationsPath, currentDomain, ref, renderRoutes } from './render';

const session = vi.hoisted(() => ({ domain: {} as Record<string, unknown> }));

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/applications', () => ({
    patchApplication: vi.fn(),
    updateApplicationType: vi.fn(),
    deleteApplication: vi.fn(),
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => ({ ...currentDomain, domain: session.domain }) }));

const appPath = `${applicationsPath}/app-1`;

const portal: Application = {
    id: 'app-1',
    name: 'Portal',
    type: 'web',
    enabled: true,
    description: 'The customer portal',
    template: false,
    settings: {
        oauth: {
            redirectUris: ['https://portal.acme/callback'],
            postLogoutRedirectUris: ['https://portal.acme/bye'],
            requestUris: [],
            singleSignOut: false,
            silentReAuthentication: true,
        },
        advanced: { skipConsent: false },
    },
};

const EDITOR = ['application_settings_read', 'application_settings_update', 'application_openid_read', 'application_openid_update'];
const OWNER = [...EDITOR, 'application_settings_delete'];

function renderPage(permissions: string[], application: Application = portal, domain: Record<string, unknown> = {}) {
    session.domain = { ...currentDomain.domain, ...domain };
    return renderRoutes(
        `${appPath}/settings/general`,
        <>
            <Route
                path={`${appPath}/settings/general`}
                element={
                    <ApplicationContext value={{ application, applicationPath: appPath, listPath: applicationsPath }}>
                        <ApplicationGeneralPage />
                    </ApplicationContext>
                }
            />
            <Route path={appPath} element={<p>Application root</p>} />
            <Route path={applicationsPath} element={<p>Applications list</p>} />
        </>,
        permissions,
    );
}

function confirmIn(title: string, label: string) {
    fireEvent.click(within(screen.getByRole('dialog', { name: title })).getByRole('button', { name: label }));
}

beforeEach(() => {
    vi.clearAllMocks();
});

describe('ApplicationGeneralPage', () => {
    it('saves the general fields', async () => {
        vi.mocked(patchApplication).mockResolvedValue({ ...portal, name: 'Portal v2' });
        renderPage(EDITOR);

        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Portal v2' } });
        fireEvent.change(screen.getByRole('textbox', { name: 'Add redirect URI' }), { target: { value: ' https://portal.acme/cb2 ' } });
        fireEvent.keyDown(screen.getByRole('textbox', { name: 'Add redirect URI' }), { key: 'Enter' });
        fireEvent.click(screen.getByRole('switch', { name: /Single sign-out/ }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchApplication).toHaveBeenCalledWith(ref, 'app-1', {
                name: 'Portal v2',
                description: 'The customer portal',
                settings: {
                    oauth: {
                        redirectUris: ['https://portal.acme/callback', 'https://portal.acme/cb2'],
                        requestUris: [],
                        postLogoutRedirectUris: ['https://portal.acme/bye'],
                        singleSignOut: true,
                        silentReAuthentication: true,
                    },
                    advanced: { skipConsent: false },
                },
                template: false,
            }),
        );
        expect(toast.success).toHaveBeenCalledWith('Application updated');
    });

    it('saves on Enter in the name', async () => {
        vi.mocked(patchApplication).mockResolvedValue(portal);
        renderPage(EDITOR);

        const name = screen.getByRole<HTMLInputElement>('textbox', { name: 'Name' });
        fireEvent.change(name, { target: { value: 'Portal v2' } });
        fireEvent.submit(name.form ?? name);

        await waitFor(() => expect(patchApplication).toHaveBeenCalledWith(ref, 'app-1', expect.objectContaining({ name: 'Portal v2' })));
    });

    it('leaves the OAuth settings out of the save, read-only, without application_openid_update', async () => {
        vi.mocked(patchApplication).mockResolvedValue(portal);
        renderPage(['application_settings_read', 'application_settings_update']);

        expect(screen.queryByRole('textbox', { name: 'Add redirect URI' })).not.toBeInTheDocument();
        expect(screen.getByRole('switch', { name: /Single sign-out/ })).toBeDisabled();
        fireEvent.click(screen.getByRole('switch', { name: /Skip user consent/ }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchApplication).toHaveBeenCalledWith(ref, 'app-1', {
                name: 'Portal',
                description: 'The customer portal',
                settings: { advanced: { skipConsent: true } },
                template: false,
            }),
        );
    });

    it('shows Insufficient permission in place of the OAuth settings the API leaves out', () => {
        renderPage(EDITOR, { ...portal, settings: { advanced: { skipConsent: false } } });

        expect(screen.getAllByText('Insufficient permission')).toHaveLength(3);
        expect(screen.queryByText('There are no redirect URIs.')).not.toBeInTheDocument();
        expect(screen.queryByRole('switch', { name: /Single sign-out/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('switch', { name: /Silent re-authentication/ })).not.toBeInTheDocument();
    });

    it('rejects a redirect URI that is already listed', () => {
        renderPage(EDITOR);

        fireEvent.change(screen.getByRole('textbox', { name: 'Add redirect URI' }), { target: { value: 'https://portal.acme/callback' } });
        fireEvent.keyDown(screen.getByRole('textbox', { name: 'Add redirect URI' }), { key: 'Enter' });

        expect(toast.error).toHaveBeenCalledWith('Error : redirect URI "https://portal.acme/callback" already exists');
        expect(screen.queryByText('You have unsaved changes.')).not.toBeInTheDocument();
    });

    it('removes a URI after the confirmation', () => {
        renderPage(EDITOR);

        fireEvent.click(screen.getByRole('button', { name: 'Remove https://portal.acme/bye' }));
        confirmIn('Remove logout redirect URI', 'Remove');

        expect(screen.queryByText('https://portal.acme/bye')).not.toBeInTheDocument();
        expect(screen.getByText('You have unsaved changes.')).toBeInTheDocument();
    });

    it('shows the name error once the field loses focus, and blocks the save', () => {
        renderPage(EDITOR);
        const name = screen.getByRole('textbox', { name: 'Name' });

        fireEvent.change(name, { target: { value: '' } });
        expect(screen.queryByText('A name is required.')).not.toBeInTheDocument();
        fireEvent.blur(name);

        expect(screen.getByText('A name is required.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('hides the URIs and the sign-in toggles from a backend to backend application', () => {
        renderPage(EDITOR, { ...portal, type: 'service' });

        expect(screen.queryByRole('textbox', { name: 'Add redirect URI' })).not.toBeInTheDocument();
        expect(screen.queryByRole('switch', { name: /Single sign-out/ })).not.toBeInTheDocument();
        expect(screen.getByRole('switch', { name: /Use as registration template/ })).toBeInTheDocument();
    });

    it('says whether the redirect URIs support expression language', () => {
        renderPage(EDITOR, portal, { oidc: { clientRegistrationSettings: { allowRedirectUriParamsExpressionLanguage: true } } });

        expect(screen.getByText(/Supports expression language and dynamic parameters\./)).toBeInTheDocument();
    });

    it('locks the template mode of the CIMD template', () => {
        renderPage(EDITOR, portal, { oidc: { cimdSettings: { enabled: true, templateId: 'app-1' } } });

        expect(screen.getByRole('switch', { name: /Use as registration template/ })).toBeDisabled();
    });

    it('is read-only without the update permission', () => {
        renderPage(['application_settings_read']);

        expect(screen.getByRole('textbox', { name: 'Name' })).toBeDisabled();
        expect(screen.queryByRole('textbox', { name: 'Add redirect URI' })).not.toBeInTheDocument();
        expect(screen.getByRole('switch', { name: /Silent re-authentication/ })).toBeDisabled();
        expect(screen.queryByText('Danger zone')).not.toBeInTheDocument();
    });

    it('offers the delete without the type change to a user who cannot update', () => {
        renderPage(['application_settings_read', 'application_settings_delete']);

        expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument();
        expect(screen.queryByRole('combobox', { name: 'Application type' })).not.toBeInTheDocument();
    });

    it('changes the type after the confirmation, then opens the application again', async () => {
        vi.mocked(updateApplicationType).mockResolvedValue({ ...portal, type: 'service' });
        renderPage(OWNER);

        const type = selectHarness({ name: 'Application type' });
        await type.open();
        expect(type.getOptions()).toEqual(['Web', 'Single-Page App', 'Native', 'Backend to Backend']);
        await type.close();
        await type.selectOption('Backend to Backend');
        confirmIn('Change application type', 'Change type');

        await waitFor(() => expect(updateApplicationType).toHaveBeenCalledWith(ref, 'app-1', 'SERVICE'));
        expect(toast.success).toHaveBeenCalledWith('Application type changed');
        expect(await screen.findByText('Application root')).toBeInTheDocument();
    });

    it('offers the resource server type when UMA is on', async () => {
        renderPage(OWNER, portal, { uma: { enabled: true } });

        const type = selectHarness({ name: 'Application type' });
        await type.open();

        expect(type.getOptions()).toContain('Resource Server');
    });

    it('deletes the application after the confirmation, then opens the list', async () => {
        vi.mocked(deleteApplication).mockResolvedValue(undefined);
        renderPage(OWNER);

        fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
        confirmIn('Delete Application', 'Delete');

        await waitFor(() => expect(deleteApplication).toHaveBeenCalledWith(ref, 'app-1'));
        expect(toast.success).toHaveBeenCalledWith('Application deleted');
        expect(await screen.findByText('Applications list')).toBeInTheDocument();
    });
});
