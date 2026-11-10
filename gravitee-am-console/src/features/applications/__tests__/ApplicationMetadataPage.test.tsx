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
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { type Application, patchApplication } from '../../../lib/api/applications';
import { ApplicationContext } from '../ApplicationContext';
import { ApplicationMetadataPage } from '../ApplicationMetadataPage';
import { applicationsPath, currentDomain, ref, renderRoutes } from './render';

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/applications', () => ({ patchApplication: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

const appPath = `${applicationsPath}/app-1`;

const portal: Application = { id: 'app-1', name: 'Portal', type: 'web', enabled: true, metadata: { team: 'blue', tier: 'gold' } };

const EDITOR = ['application_settings_read', 'application_settings_update'];

function renderPage(permissions: string[], application: Application = portal) {
    return renderRoutes(
        `${appPath}/settings/metadata`,
        <Route
            path={`${appPath}/settings/metadata`}
            element={
                <ApplicationContext value={{ application, applicationPath: appPath, listPath: applicationsPath }}>
                    <ApplicationMetadataPage />
                </ApplicationContext>
            }
        />,
        permissions,
    );
}

function addEntry(name: string, value: string) {
    fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: name } });
    fireEvent.change(screen.getByRole('textbox', { name: 'Value' }), { target: { value } });
    fireEvent.click(screen.getByRole('button', { name: 'Add' }));
}

beforeEach(() => {
    vi.clearAllMocks();
});

describe('ApplicationMetadataPage', () => {
    it('saves the whole map after an add, an edit and a removal', async () => {
        vi.mocked(patchApplication).mockResolvedValue({ ...portal, metadata: { team: 'red', region: 'eu' } });
        renderPage(EDITOR);

        addEntry('region', 'eu');
        fireEvent.change(screen.getByRole('textbox', { name: 'Value of team' }), { target: { value: 'red' } });
        fireEvent.click(screen.getByRole('button', { name: 'Remove tier' }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(patchApplication).toHaveBeenCalledWith(ref, 'app-1', { metadata: { team: 'red', region: 'eu' } }));
        expect(toast.success).toHaveBeenCalledWith('Application updated');
    });

    it('sends the values that are not text back unchanged, and an edited one as typed', async () => {
        const stored = { ...portal, metadata: { team: 'blue', limit: 5, cfg: { a: 1 } } };
        vi.mocked(patchApplication).mockResolvedValue(stored);
        renderPage(EDITOR, stored);

        expect(screen.getByRole('textbox', { name: 'Value of cfg' })).toHaveValue('{"a":1}');
        fireEvent.change(screen.getByRole('textbox', { name: 'Value of team' }), { target: { value: 'red' } });
        fireEvent.change(screen.getByRole('textbox', { name: 'Value of limit' }), { target: { value: '7' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchApplication).toHaveBeenCalledWith(ref, 'app-1', { metadata: { team: 'red', limit: '7', cfg: { a: 1 } } }),
        );
    });

    it('rejects a name that already exists on add', () => {
        renderPage(EDITOR);

        addEntry('team', 'green');

        expect(toast.error).toHaveBeenCalledWith('Error : metadata "team" already exists');
        expect(screen.getAllByRole('textbox', { name: 'Metadata name' })).toHaveLength(2);
    });

    it('blocks the save when a rename duplicates a name', () => {
        renderPage(EDITOR);

        const [, tier] = screen.getAllByRole('textbox', { name: 'Metadata name' });
        fireEvent.change(tier!, { target: { value: 'team' } });
        fireEvent.blur(tier!);

        expect(screen.getByText('Metadata "team" already exists.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('enables Add only with a name and a value', () => {
        renderPage(EDITOR);

        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'region' } });

        expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled();
    });

    it('shows the empty state without metadata', () => {
        renderPage(EDITOR, { ...portal, metadata: undefined });

        expect(screen.getByText('There is no metadata.')).toBeInTheDocument();
    });

    it('is read-only without the update permission', () => {
        renderPage(['application_settings_read']);

        expect(screen.getByText('gold')).toBeInTheDocument();
        expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /^Remove / })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });
});
