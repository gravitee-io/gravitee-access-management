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
import { buttonHarness, inputHarness } from '@gravitee/graphene-core/testing';
import { screen, waitFor, within } from '@testing-library/react';
import { Route } from 'react-router-dom';
import {
    createOrganizationEntrypoint,
    deleteOrganizationEntrypoint,
    getOrganizationEntrypoint,
    listOrganizationEntrypoints,
    updateOrganizationEntrypoint,
} from '../../../lib/api/entrypoints';
import { HttpError } from '../../../lib/api/http';
import { getInstallationConfiguration } from '../../../lib/api/management-api';
import { listTags } from '../../../lib/api/tags';
import { NewOrganizationEntrypointPage } from '../NewOrganizationEntrypointPage';
import { OrganizationEntrypointPage } from '../OrganizationEntrypointPage';
import { OrganizationEntrypointsPage } from '../OrganizationEntrypointsPage';
import { currentEnvironment, ORG, organizationPath, renderRoutes, session } from './render';

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/entrypoints', () => ({
    listOrganizationEntrypoints: vi.fn(),
    getOrganizationEntrypoint: vi.fn(),
    createOrganizationEntrypoint: vi.fn(),
    updateOrganizationEntrypoint: vi.fn(),
    deleteOrganizationEntrypoint: vi.fn(),
}));
vi.mock('../../../lib/api/tags', () => ({ listTags: vi.fn() }));
vi.mock('../../../lib/api/management-api', () => ({ getInstallationConfiguration: vi.fn() }));
vi.mock('../../../lib/session/session', () => ({ useSession: () => session }));
vi.mock('../../../lib/session/environment', () => ({ useCurrentEnvironment: () => currentEnvironment }));

const entrypointsPath = `${organizationPath}/entrypoints`;
const ALL = [
    'organization_entrypoint_list',
    'organization_entrypoint_create',
    'organization_entrypoint_read',
    'organization_entrypoint_update',
    'organization_entrypoint_delete',
];
const fallback = {
    id: 'default',
    name: 'Default',
    description: 'Default entrypoint',
    url: 'http://localhost:8092',
    defaultEntrypoint: true,
    environmentId: 'env-1',
};
const eu = { id: 'eu-entry', name: 'Europe', url: 'https://eu.example.com', tags: ['eu'], environmentId: 'env-2' };

function renderEntrypoints(path: string, permissions = ALL) {
    return renderRoutes(
        path,
        <>
            <Route path={entrypointsPath} element={<OrganizationEntrypointsPage />} />
            <Route path={`${entrypointsPath}/new`} element={<NewOrganizationEntrypointPage />} />
            <Route path={`${entrypointsPath}/:entrypointId`} element={<OrganizationEntrypointPage />} />
        </>,
        permissions,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getInstallationConfiguration).mockResolvedValue({ type: 'standalone' });
    vi.mocked(listOrganizationEntrypoints).mockResolvedValue([eu, fallback]);
    vi.mocked(getOrganizationEntrypoint).mockResolvedValue(eu);
    vi.mocked(listTags).mockResolvedValue([
        { id: 'eu', name: 'Europe' },
        { id: 'us', name: 'United States' },
    ]);
});

describe('OrganizationEntrypointsPage', () => {
    it('lists the entrypoints with the environment name and marks the default one', async () => {
        renderEntrypoints(entrypointsPath);

        const row = (await screen.findByRole('link', { name: 'Europe' })).closest('tr')!;
        expect(row).toHaveTextContent('https://eu.example.com');
        expect(within(row).getByText('Prod')).toBeInTheDocument();
        expect(listOrganizationEntrypoints).toHaveBeenCalledWith(ORG);
        expect(
            within(screen.getByRole('link', { name: 'Default' }).closest('tr')!).getByText('Default', { selector: 'span' }),
        ).toBeInTheDocument();
    });

    it('offers no delete on the default entrypoint', async () => {
        renderEntrypoints(entrypointsPath);

        expect(await screen.findByRole('button', { name: 'Delete Europe' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete Default' })).not.toBeInTheDocument();
    });

    it('shows the first-use state when there is no entrypoint', async () => {
        vi.mocked(listOrganizationEntrypoints).mockResolvedValueOnce([]);

        renderEntrypoints(entrypointsPath);

        expect(await screen.findByText('Entrypoints will appear here')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Create entrypoint' })).toHaveAttribute('href', `${entrypointsPath}/new`);
    });

    it('hides the create, edit and delete actions when the installation type cannot be loaded', async () => {
        vi.mocked(getInstallationConfiguration).mockRejectedValue(new HttpError(500, 'Service unavailable'));

        renderEntrypoints(entrypointsPath);

        expect(await screen.findByText('Europe')).toBeInTheDocument();
        await waitFor(() => expect(getInstallationConfiguration).toHaveBeenCalled());
        expect(screen.queryByRole('link', { name: 'Create entrypoint' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete Europe' })).not.toBeInTheDocument();
    });

    it('hides the create, edit and delete actions in cloud mode', async () => {
        vi.mocked(getInstallationConfiguration).mockResolvedValue({ type: 'managed' });

        renderEntrypoints(entrypointsPath);

        expect(await screen.findByText('Europe')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Europe' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Create entrypoint' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete Europe' })).not.toBeInTheDocument();
    });

    it('hides the actions without their permissions', async () => {
        renderEntrypoints(entrypointsPath, ['organization_entrypoint_list']);

        expect(await screen.findByText('Europe')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Europe' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Create entrypoint' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete Europe' })).not.toBeInTheDocument();
    });

    it('deletes an entrypoint after the confirmation and reloads the list', async () => {
        vi.mocked(deleteOrganizationEntrypoint).mockResolvedValueOnce(undefined);
        renderEntrypoints(entrypointsPath);

        await screen.findByRole('button', { name: 'Delete Europe' });
        await buttonHarness({ name: 'Delete Europe' }).click();
        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent('Are you sure you want to delete this entrypoint?');
        await buttonHarness({ name: 'Delete', within: dialog }).click();

        await waitFor(() => expect(deleteOrganizationEntrypoint).toHaveBeenCalledWith(ORG, 'eu-entry'));
        await waitFor(() => expect(listOrganizationEntrypoints).toHaveBeenCalledTimes(2));
        expect(toast.success).toHaveBeenCalledWith('Entrypoint deleted');
    });
});

describe('NewOrganizationEntrypointPage', () => {
    it('creates the entrypoint with no tag once the url is valid', async () => {
        vi.mocked(createOrganizationEntrypoint).mockResolvedValueOnce(eu);
        renderEntrypoints(`${entrypointsPath}/new`);

        await inputHarness({ name: 'Name' }).type('Europe');
        await inputHarness({ name: 'Entrypoint url' }).type('eu.example.com');
        expect(buttonHarness({ name: 'Create' }).isDisabled()).toBe(true);
        expect(screen.getByRole('textbox', { name: 'Entrypoint url' })).toHaveAccessibleDescription(
            'The url must start with http:// or https://.',
        );

        await inputHarness({ name: 'Entrypoint url' }).clear();
        await inputHarness({ name: 'Entrypoint url' }).type('https://eu.example.com');
        await buttonHarness({ name: 'Create' }).click();

        await waitFor(() =>
            expect(createOrganizationEntrypoint).toHaveBeenCalledWith(ORG, {
                name: 'Europe',
                description: '',
                url: 'https://eu.example.com',
                tags: [],
            }),
        );
        expect(toast.success).toHaveBeenCalledWith('Entrypoint Europe created');
    });
});

describe('OrganizationEntrypointPage', () => {
    it('saves the name, description, url and tags, with a removed tag', async () => {
        vi.mocked(updateOrganizationEntrypoint).mockResolvedValueOnce(eu);
        renderEntrypoints(`${entrypointsPath}/eu-entry`);

        expect(await screen.findByText('eu | Europe')).toBeInTheDocument();
        await waitFor(() => expect(inputHarness({ name: 'Entrypoint url' }).getElement()).not.toHaveAttribute('readonly'));
        await inputHarness({ name: 'Entrypoint url' }).clear();
        await inputHarness({ name: 'Entrypoint url' }).type('https://europe.example.com');
        await buttonHarness({ name: 'Remove sharding tag eu' }).click();
        await buttonHarness({ name: 'Save' }).click();

        await waitFor(() =>
            expect(updateOrganizationEntrypoint).toHaveBeenCalledWith(ORG, 'eu-entry', {
                name: 'Europe',
                description: '',
                url: 'https://europe.example.com',
                tags: [],
            }),
        );
        expect(toast.success).toHaveBeenCalledWith('Entrypoint updated');
    });

    it('keeps the name, description and tags of the default entrypoint read-only and offers no delete', async () => {
        vi.mocked(getOrganizationEntrypoint).mockResolvedValueOnce(fallback);
        renderEntrypoints(`${entrypointsPath}/default`);

        expect(await screen.findByRole('textbox', { name: 'Name' })).toBeDisabled();
        expect(screen.getByRole('textbox', { name: 'Description' })).toBeDisabled();
        expect(screen.getByRole('textbox', { name: 'Entrypoint url' })).toBeEnabled();
        expect(screen.queryByRole('button', { name: /Remove sharding tag/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });

    it('is read-only in cloud mode, with no Save and no Delete', async () => {
        vi.mocked(getInstallationConfiguration).mockResolvedValue({ type: 'managed' });
        renderEntrypoints(`${entrypointsPath}/eu-entry`);

        expect(await screen.findByRole('textbox', { name: 'Entrypoint url' })).toHaveAttribute('readonly');
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });

    it('shows the not found panel for an unknown entrypoint', async () => {
        vi.mocked(getOrganizationEntrypoint).mockRejectedValueOnce(new HttpError(404, 'Not found'));

        renderEntrypoints(`${entrypointsPath}/unknown`);

        expect(await screen.findByText('Entrypoint not found')).toBeInTheDocument();
    });
});
