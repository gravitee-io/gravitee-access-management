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
import { createOrganizationForm, deleteOrganizationForm, getOrganizationForm, updateOrganizationForm } from '../../../lib/api/forms';
import { OrganizationFormPage } from '../OrganizationFormPage';
import { OrganizationFormsPage } from '../OrganizationFormsPage';
import { currentEnvironment, ORG, organizationPath, renderRoutes, session } from './render';

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('@gravitee/graphene-core/code-editor', () => ({
    CodeEditor: ({ value, readOnly, onChange }: { value: string; readOnly?: boolean; onChange: (value: string) => void }) => (
        <textarea aria-label="HTML editor" value={value} readOnly={readOnly} onChange={event => onChange(event.target.value)} />
    ),
}));
vi.mock('../../../lib/api/forms', () => ({
    getOrganizationForm: vi.fn(),
    createOrganizationForm: vi.fn(),
    updateOrganizationForm: vi.fn(),
    deleteOrganizationForm: vi.fn(),
}));
vi.mock('../../../lib/session/session', () => ({ useSession: () => session }));
vi.mock('../../../lib/session/environment', () => ({ useCurrentEnvironment: () => currentEnvironment }));

const formsPath = `${organizationPath}/forms`;
const formPath = `${formsPath}/form?template=LOGIN`;
const ALL = [
    'organization_form_list',
    'organization_form_read',
    'organization_form_create',
    'organization_form_update',
    'organization_form_delete',
];
const custom = { id: 'form-1', template: 'LOGIN', enabled: true, content: '<html>custom</html>' };

function renderForms(path: string, permissions = ALL) {
    return renderRoutes(
        path,
        <>
            <Route path={formsPath} element={<OrganizationFormsPage />} />
            <Route path={`${formsPath}/form`} element={<OrganizationFormPage />} />
        </>,
        permissions,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getOrganizationForm).mockResolvedValue(custom);
});

describe('OrganizationFormsPage', () => {
    it('lists the login template with a link to its editor', () => {
        renderForms(formsPath);

        expect(screen.getByText('Login page to authenticate users')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Edit Login template' })).toHaveAttribute('href', formPath);
    });

    it('replaces the link with a no-permission mark without organization_form_read', () => {
        renderForms(formsPath, ['organization_form_list']);

        expect(screen.queryByRole('link', { name: 'Edit Login template' })).not.toBeInTheDocument();
        expect(screen.getByText("You don't have required permission")).toBeInTheDocument();
    });
});

describe('OrganizationFormPage', () => {
    it('loads the form of the template and updates it with the enabled flag and the content only', async () => {
        vi.mocked(updateOrganizationForm).mockResolvedValueOnce({ ...custom, content: '<html>edited</html>' });
        renderForms(formPath);

        const editor = await screen.findByRole('textbox', { name: 'HTML editor' });
        expect(editor).toHaveValue('<html>custom</html>');
        expect(getOrganizationForm).toHaveBeenCalledWith(ORG, 'LOGIN');
        fireEvent.change(editor, { target: { value: '<html>edited</html>' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateOrganizationForm).toHaveBeenCalledWith(ORG, 'form-1', {
                template: 'LOGIN',
                enabled: true,
                content: '<html>edited</html>',
            }),
        );
        expect(toast.success).toHaveBeenCalledWith('Form updated');
    });

    it('creates the form when the organization has none, starting from the disabled placeholder', async () => {
        vi.mocked(getOrganizationForm).mockResolvedValue({ template: 'login', enabled: false });
        vi.mocked(createOrganizationForm).mockResolvedValueOnce(custom);
        renderForms(formPath);

        const editor = await screen.findByRole('textbox', { name: 'HTML editor' });
        expect(editor).toHaveValue('// Custom form...');
        expect(editor).toHaveAttribute('readonly');
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('switch', { name: /Enable custom login form/ }));
        fireEvent.change(editor, { target: { value: '<html>custom</html>' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(createOrganizationForm).toHaveBeenCalledWith(ORG, { template: 'LOGIN', enabled: true, content: '<html>custom</html>' }),
        );
        expect(toast.success).toHaveBeenCalledWith('Form created');
    });

    it('shows Save only once something changes, and keeps the preview tab from submitting', async () => {
        renderForms(formPath);

        const editor = await screen.findByRole('textbox', { name: 'HTML editor' });
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        fireEvent.change(editor, { target: { value: '<html>edited</html>' } });
        expect(await screen.findByRole('button', { name: 'Save' })).toBeEnabled();

        await userEvent.click(screen.getByRole('tab', { name: 'Preview' }));

        expect(screen.getByTitle('Preview')).toHaveAttribute('sandbox', '');
        expect(updateOrganizationForm).not.toHaveBeenCalled();
    });

    it('deletes the form after the confirmation and reloads it', async () => {
        vi.mocked(deleteOrganizationForm).mockResolvedValueOnce(undefined);
        renderForms(formPath);

        fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));
        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent('Are you sure you want to delete this form?');
        vi.mocked(getOrganizationForm).mockResolvedValue({ template: 'login', enabled: false });
        fireEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));

        await waitFor(() => expect(deleteOrganizationForm).toHaveBeenCalledWith(ORG, 'form-1'));
        expect(await screen.findByDisplayValue('// Custom form...')).toBeInTheDocument();
        expect(toast.success).toHaveBeenCalledWith('Form deleted');
    });

    it('is read-only without the update and delete permissions', async () => {
        renderForms(formPath, ['organization_form_list', 'organization_form_read']);

        expect(await screen.findByRole('textbox', { name: 'HTML editor' })).toHaveAttribute('readonly');
        expect(screen.getByRole('switch', { name: /Enable custom login form/ })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });

    it('creates only with organization_form_create when the form does not exist', async () => {
        vi.mocked(getOrganizationForm).mockResolvedValue({ template: 'login', enabled: false });
        renderForms(formPath, ['organization_form_list', 'organization_form_read', 'organization_form_update']);

        expect(await screen.findByRole('textbox', { name: 'HTML editor' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });

    it('shows the not found panel without a template', async () => {
        renderForms(`${formsPath}/form`);

        expect(await screen.findByText('Form not found')).toBeInTheDocument();
        expect(getOrganizationForm).not.toHaveBeenCalled();
    });
});
