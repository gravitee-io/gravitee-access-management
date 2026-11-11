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
import { screen, waitFor } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { HttpError } from '../../../lib/api/http';
import { createTag, deleteTag, getTag, listTags, updateTag } from '../../../lib/api/tags';
import { NewTagPage } from '../NewTagPage';
import { TagPage } from '../TagPage';
import { TagsPage } from '../TagsPage';
import { ORG, organizationPath, currentEnvironment, renderRoutes, session } from './render';

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/tags', () => ({
    listTags: vi.fn(),
    getTag: vi.fn(),
    createTag: vi.fn(),
    updateTag: vi.fn(),
    deleteTag: vi.fn(),
}));
vi.mock('../../../lib/session/session', () => ({ useSession: () => session }));
vi.mock('../../../lib/session/environment', () => ({ useCurrentEnvironment: () => currentEnvironment }));

const tagsPath = `${organizationPath}/tags`;
const ALL = [
    'organization_tag_list',
    'organization_tag_create',
    'organization_tag_read',
    'organization_tag_update',
    'organization_tag_delete',
];
const eu = { id: 'eu', name: 'Europe', description: 'European gateways' };

function renderTags(path: string, permissions = ALL) {
    return renderRoutes(
        path,
        <>
            <Route path={tagsPath} element={<TagsPage />} />
            <Route path={`${tagsPath}/new`} element={<NewTagPage />} />
            <Route path={`${tagsPath}/:tagId`} element={<TagPage />} />
        </>,
        permissions,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listTags).mockResolvedValue([
        { id: 'us', name: 'United States' },
        { id: 'eu', name: 'Europe', description: 'European gateways' },
    ]);
    vi.mocked(getTag).mockResolvedValue(eu);
});

describe('TagsPage', () => {
    it('lists the tags of the organization sorted by name', async () => {
        renderTags(tagsPath);

        const europe = await screen.findByRole('link', { name: 'Europe' });
        expect(europe).toHaveAttribute('href', `${tagsPath}/eu`);
        expect(listTags).toHaveBeenCalledWith(ORG);
        const names = screen.getAllByRole('link').map(link => link.textContent);
        expect(names.indexOf('Europe')).toBeLessThan(names.indexOf('United States'));
        expect(screen.getByText('European gateways')).toBeInTheDocument();
    });

    it('shows the first-use state with the create link when there is no tag', async () => {
        vi.mocked(listTags).mockResolvedValueOnce([]);

        renderTags(tagsPath);

        expect(await screen.findByText('Sharding tags will appear here')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Create sharding tag' })).toHaveAttribute('href', `${tagsPath}/new`);
    });

    it('shows the error panel, not the first-use state, when the list fails', async () => {
        vi.mocked(listTags).mockRejectedValueOnce(new HttpError(500, 'Service unavailable'));

        renderTags(tagsPath);

        expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the sharding tags.');
        expect(screen.queryByText('Sharding tags will appear here')).not.toBeInTheDocument();
    });

    it('loads the list again when Retry is clicked after a failure', async () => {
        vi.mocked(listTags).mockRejectedValueOnce(new HttpError(500, 'Service unavailable'));

        renderTags(tagsPath);
        await screen.findByRole('button', { name: 'Retry' });
        await buttonHarness({ name: 'Retry' }).click();

        expect(await screen.findByRole('link', { name: 'Europe' })).toBeInTheDocument();
        expect(listTags).toHaveBeenCalledTimes(2);
    });

    it('shows the name column before the id column', async () => {
        renderTags(tagsPath);

        await screen.findByRole('link', { name: 'Europe' });
        const headers = screen.getAllByRole('columnheader').map(header => header.textContent);
        expect(headers.indexOf('Name')).toBeLessThan(headers.indexOf('ID'));
    });

    it('hides the create, edit and delete actions without their permissions', async () => {
        renderTags(tagsPath, ['organization_tag_list']);

        expect(await screen.findByText('Europe')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Europe' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Create sharding tag' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete Europe' })).not.toBeInTheDocument();
    });

    it('deletes a tag after the confirmation and reloads the list', async () => {
        vi.mocked(deleteTag).mockResolvedValueOnce(undefined);
        renderTags(tagsPath);

        await screen.findByRole('button', { name: 'Delete Europe' });
        await buttonHarness({ name: 'Delete Europe' }).click();
        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent('Are you sure you want to delete this sharding tag?');
        await buttonHarness({ name: 'Delete', within: dialog }).click();

        await waitFor(() => expect(deleteTag).toHaveBeenCalledWith(ORG, 'eu'));
        await waitFor(() => expect(listTags).toHaveBeenCalledTimes(2));
        expect(toast.success).toHaveBeenCalledWith('Sharding tag deleted');
    });
});

describe('NewTagPage', () => {
    it('creates the tag with its name and description and opens it', async () => {
        vi.mocked(createTag).mockResolvedValueOnce(eu);
        renderTags(`${tagsPath}/new`);

        expect(buttonHarness({ name: 'Create' }).isDisabled()).toBe(true);
        await inputHarness({ name: 'Name' }).type('Europe');
        await inputHarness({ name: 'Description' }).type('European gateways');
        await buttonHarness({ name: 'Create' }).click();

        await waitFor(() => expect(createTag).toHaveBeenCalledWith(ORG, { name: 'Europe', description: 'European gateways' }));
        expect(toast.success).toHaveBeenCalledWith('Sharding Tag Europe created');
        expect(await screen.findByRole('heading', { name: 'Europe' })).toBeInTheDocument();
    });
});

describe('TagPage', () => {
    it('saves the name and the description only', async () => {
        vi.mocked(updateTag).mockResolvedValueOnce({ ...eu, name: 'EU' });
        renderTags(`${tagsPath}/eu`);

        await screen.findByRole('textbox', { name: 'Name' });
        await inputHarness({ name: 'Name' }).clear();
        await inputHarness({ name: 'Name' }).type('EU');
        await buttonHarness({ name: 'Save' }).click();

        await waitFor(() => expect(updateTag).toHaveBeenCalledWith(ORG, 'eu', { name: 'EU', description: 'European gateways' }));
        expect(toast.success).toHaveBeenCalledWith('Sharding tag updated');
    });

    it('shows the id read-only and no Save or Delete without the update and delete permissions', async () => {
        renderTags(`${tagsPath}/eu`, ['organization_tag_list', 'organization_tag_read']);

        expect(await screen.findByRole('textbox', { name: 'ID' })).toBeDisabled();
        expect(screen.getByRole('textbox', { name: 'Name' })).toHaveAttribute('readonly');
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });

    it('deletes the tag after the confirmation and opens the list', async () => {
        vi.mocked(deleteTag).mockResolvedValueOnce(undefined);
        renderTags(`${tagsPath}/eu`);

        await screen.findByRole('button', { name: 'Delete' });
        await buttonHarness({ name: 'Delete' }).click();
        const dialog = await screen.findByRole('dialog');
        await buttonHarness({ name: 'Delete', within: dialog }).click();

        await waitFor(() => expect(deleteTag).toHaveBeenCalledWith(ORG, 'eu'));
        expect(await screen.findByRole('heading', { name: 'Sharding tags' })).toBeInTheDocument();
    });

    it('shows the not found panel for an unknown tag', async () => {
        vi.mocked(getTag).mockRejectedValueOnce(new HttpError(404, 'Not found'));

        renderTags(`${tagsPath}/unknown`);

        expect(await screen.findByText('Sharding tag not found')).toBeInTheDocument();
    });
});
