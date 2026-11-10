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
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route } from 'react-router-dom';
import { type Audit, getUser, listAuditEventTypes, listUserAudits, type User } from '../../../lib/api/users';
import { UserHistoryPage } from '../UserHistoryPage';
import { UserLayout } from '../UserLayout';
import { currentDomain, ref, renderRoutes, usersPath } from './render';

vi.mock('../../../lib/api/users', async importOriginal => ({
    ...(await importOriginal<object>()),
    getUser: vi.fn(),
    listAuditEventTypes: vi.fn(),
    listUserAudits: vi.fn(),
}));
vi.mock('@gravitee/graphene-core/code-editor', () => ({
    CodeEditor: ({ value }: { value: string }) => <pre data-testid="audit-details">{value}</pre>,
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

const alice: User = { id: 'u-1', username: 'alice', displayName: 'Alice', enabled: true, accountNonLocked: true };
const login: Audit = {
    id: 'a-1',
    type: 'USER_LOGIN',
    timestamp: 1_700_000_000_000,
    outcome: { status: 'success', message: '{"client":"portal"}' },
    accessPoint: { ipAddress: '10.0.0.1', userAgent: 'curl' },
};

function setup(permissions = ['domain_user_read', 'domain_audit_read']) {
    vi.mocked(getUser).mockResolvedValue(alice);
    renderRoutes(
        `${usersPath}/u-1/history`,
        <Route path={`${usersPath}/:userId`} element={<UserLayout />}>
            <Route path="history" element={<UserHistoryPage />} />
        </Route>,
        permissions,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listAuditEventTypes).mockResolvedValue(['USER_LOGIN', 'USER_LOGOUT']);
    vi.mocked(listUserAudits).mockResolvedValue({ data: [login], currentPage: 0, totalCount: 1 });
});

describe('UserHistoryPage', () => {
    it('lists the audit events of the last day, 10 at a time', async () => {
        const before = Date.now();
        setup();

        expect(await screen.findByText('USER_LOGIN')).toBeInTheDocument();
        expect(screen.getByText('Success')).toBeInTheDocument();
        expect(screen.getByRole('region', { name: 'User history' })).toHaveTextContent('2023');
        const query = vi.mocked(listUserAudits).mock.calls[0][2];
        expect(vi.mocked(listUserAudits).mock.calls[0].slice(0, 2)).toEqual([ref, 'u-1']);
        expect(query).toMatchObject({ page: 0, size: 10, type: undefined, status: undefined });
        expect(query.to - query.from).toBe(24 * 60 * 60 * 1000);
        expect(query.to).toBeGreaterThanOrEqual(before);
    });

    it('filters on the status', async () => {
        setup();
        await screen.findByText('USER_LOGIN');

        await userEvent.click(screen.getByRole('combobox', { name: 'Status' }));
        await userEvent.click(await screen.findByRole('option', { name: 'Failure' }));

        await waitFor(() => expect(vi.mocked(listUserAudits).mock.lastCall?.[2]).toMatchObject({ status: 'FAILURE', page: 0 }));
    });

    it('shows an event without an outcome as unknown', async () => {
        vi.mocked(listUserAudits).mockResolvedValue({ data: [{ ...login, outcome: undefined }], currentPage: 0, totalCount: 1 });
        setup();

        expect(await screen.findByText('Unknown')).toBeInTheDocument();
        expect(screen.queryByText('Failure')).not.toBeInTheDocument();
    });

    it('drops the picked dates when a quick range is chosen', async () => {
        const span = () => {
            const query = vi.mocked(listUserAudits).mock.lastCall?.[2];
            return (query?.to ?? 0) - (query?.from ?? 0);
        };
        setup();
        await screen.findByText('USER_LOGIN');
        await userEvent.click(screen.getByRole('button', { name: 'Period' }));
        const days = within((await screen.findAllByRole('grid'))[0]).getAllByRole('button');
        await userEvent.click(days[10]);
        await userEvent.click(days[11]);
        await waitFor(() => expect((vi.mocked(listUserAudits).mock.lastCall?.[2].to ?? 0) % 1000).toBe(999));
        await userEvent.keyboard('{Escape}');

        await userEvent.click(screen.getByRole('combobox', { name: 'Quick time range' }));
        await userEvent.click(await screen.findByRole('option', { name: 'Last hour' }));

        await waitFor(() => expect(span()).toBe(60 * 60 * 1000));
    });

    it('opens the event details with the IP, the user agent and the indented message', async () => {
        setup();

        await userEvent.click(await screen.findByRole('button', { name: 'View USER_LOGIN details' }));

        const dialog = await screen.findByRole('dialog', { name: 'USER_LOGIN' });
        expect(within(dialog).getByText('10.0.0.1')).toBeInTheDocument();
        expect(within(dialog).getByText('curl')).toBeInTheDocument();
        expect(within(dialog).getByTestId('audit-details').textContent).toBe('{\n  "client": "portal"\n}');
    });

    it('maximizes the event details', async () => {
        setup();
        await userEvent.click(await screen.findByRole('button', { name: 'View USER_LOGIN details' }));
        const maximize = within(await screen.findByRole('dialog', { name: 'USER_LOGIN' })).getByRole('button', { name: 'Maximize' });

        await userEvent.click(maximize);

        expect(maximize).toHaveAttribute('aria-pressed', 'true');
    });

    it('hides the event details without the audit read permission', async () => {
        setup(['domain_user_read']);

        expect(await screen.findByText('USER_LOGIN')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'View USER_LOGIN details' })).not.toBeInTheDocument();
    });
});
