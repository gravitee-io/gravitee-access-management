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
import type * as Graphene from '@gravitee/graphene-core';
import { LayoutSlotsProvider, TooltipProvider, toast } from '@gravitee/graphene-core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { listBotDetections } from '../../../lib/api/bot-detection';
import { type Domain, patchDomainAccountSettings } from '../../../lib/api/domains';
import { listUserIdentityProviders } from '../../../lib/api/identity-providers';
import { PermissionScope } from '../../../lib/session/permissions';
import { DomainAccountPage } from '../DomainAccountPage';

const domain: Domain = {
    id: 'd1',
    hrid: 'acme',
    name: 'Acme',
    enabled: true,
};
const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };
const session = vi.hoisted(() => ({ domain: {} as Domain }));

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<typeof Graphene>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/domains', () => ({ patchDomainAccountSettings: vi.fn() }));
vi.mock('../../../lib/api/identity-providers', () => ({ listUserIdentityProviders: vi.fn() }));
vi.mock('../../../lib/api/bot-detection', () => ({ listBotDetections: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({
    useCurrentDomain: () => ({
        domain: session.domain,
        ref: { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' },
        basePath: '/environments/dev/domains/acme',
        domainsPath: '/environments/dev/domains',
    }),
}));

function renderPage(permissions: string[], current: Domain = domain) {
    session.domain = current;
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={client}>
            <PermissionScope permissions={permissions}>
                <MemoryRouter>
                    <LayoutSlotsProvider>
                        <TooltipProvider>
                            <DomainAccountPage />
                        </TooltipProvider>
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </PermissionScope>
        </QueryClientProvider>,
    );
}

const editor = ['domain_settings_read', 'domain_settings_update'];
const stored: Domain = {
    ...domain,
    accountSettings: {
        inherited: false,
        loginAttemptsDetectionEnabled: true,
        maxLoginAttempts: 5,
        loginAttemptsResetTime: 3_600,
        accountBlockedDuration: 86_400,
        resetPasswordConfirmIdentity: true,
        resetPasswordCustomFormFields: [{ key: 'email', label: 'Email', type: 'email' }],
        botDetectionPlugin: 'bot-1',
    },
};

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listUserIdentityProviders).mockResolvedValue([
        { id: 'idp-1', name: 'Default IdP', type: 'mongo-am-idp', system: true, external: false },
    ]);
    vi.mocked(listBotDetections).mockResolvedValue([]);
});

describe('DomainAccountPage', () => {
    it('saves the stored settings in seconds, without the custom form fields and the bot plugin that are off', async () => {
        vi.mocked(patchDomainAccountSettings).mockResolvedValueOnce(stored);
        renderPage(editor, stored);

        expect(screen.getByRole('spinbutton', { name: 'Login attempts reset time' })).toHaveValue(1);
        expect(screen.getByRole('combobox', { name: 'Login attempts reset time unit' })).toHaveTextContent('Hours');
        fireEvent.click(screen.getByRole('switch', { name: 'Send account recovery email' }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchDomainAccountSettings).toHaveBeenCalledWith(ref, {
                inherited: false,
                loginAttemptsDetectionEnabled: true,
                maxLoginAttempts: 5,
                loginAttemptsResetTime: 3_600,
                accountBlockedDuration: 86_400,
                mfaChallengeAttemptsResetTime: 0,
                rememberMeDuration: 0,
                sendRecoverAccountEmail: true,
            }),
        );
        expect(toast.success).toHaveBeenCalledWith('User Accounts Settings updated');
    });

    it('applies the Angular defaults when brute force detection is turned on', async () => {
        vi.mocked(patchDomainAccountSettings).mockResolvedValueOnce(domain);
        renderPage(editor);

        fireEvent.click(screen.getByRole('switch', { name: 'Brute force detection' }));
        expect(screen.getByRole('spinbutton', { name: 'Max login attempts' })).toHaveValue(10);
        expect(screen.getByRole('spinbutton', { name: 'Login attempts reset time' })).toHaveValue(12);
        expect(screen.getByRole('spinbutton', { name: 'Account blocked duration' })).toHaveValue(2);
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchDomainAccountSettings).toHaveBeenCalledWith(
                ref,
                expect.objectContaining({ maxLoginAttempts: 10, loginAttemptsResetTime: 43_200, accountBlockedDuration: 7_200 }),
            ),
        );
    });

    it('needs a field on the custom forgot password form, then saves it', async () => {
        vi.mocked(patchDomainAccountSettings).mockResolvedValueOnce(domain);
        renderPage(editor);

        fireEvent.click(screen.getByRole('switch', { name: 'Forgot password form with custom fields' }));
        expect(screen.getByText('Add at least one field to the custom form.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

        fireEvent.click(screen.getByRole('combobox', { name: 'Field key' }));
        fireEvent.click(await screen.findByRole('option', { name: 'Username' }));
        fireEvent.click(screen.getByRole('button', { name: 'Add' }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchDomainAccountSettings).toHaveBeenCalledWith(
                ref,
                expect.objectContaining({
                    resetPasswordCustomForm: true,
                    resetPasswordCustomFormFields: [{ key: 'username', label: 'Username', type: 'text' }],
                }),
            ),
        );
    });

    it('lets domain_settings_create alone edit, as Angular does', () => {
        renderPage(['domain_settings_read', 'domain_settings_create'], stored);

        expect(screen.getByRole('switch', { name: 'Remember me' })).toBeEnabled();
        expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument();
    });

    it('shows a read-only form without domain_settings_create and domain_settings_update', () => {
        renderPage(['domain_settings_read'], stored);

        expect(screen.getByRole('switch', { name: 'Brute force detection' })).toBeDisabled();
        expect(screen.getByRole('spinbutton', { name: 'Max login attempts' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });
});
