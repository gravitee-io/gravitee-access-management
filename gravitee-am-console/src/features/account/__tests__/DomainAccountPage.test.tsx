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
import { LayoutSlotsProvider, toast, TooltipProvider, useLayoutSlots } from '@gravitee/graphene-core';
import { renderWithGraphene } from '@gravitee/graphene-core/testing';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { listBotDetections } from '../../../lib/api/bot-detection';
import { type Domain, patchDomainAccountSettings } from '../../../lib/api/domains';
import { listUserIdentityProviders } from '../../../lib/api/identity-providers';
import { PermissionScope } from '../../../lib/session/permissions';
import { DomainAccountPage } from '../DomainAccountPage';

const domain: Domain = { id: 'd1', hrid: 'acme', name: 'Acme', enabled: true };
const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };
const session = vi.hoisted(() => ({ domain: { id: '', hrid: '', name: '', enabled: true } }));

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
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

function Banner() {
    return <>{useLayoutSlots().slots.banner}</>;
}

function renderPage(permissions: string[], current: Domain = domain) {
    session.domain = current;
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return renderWithGraphene(
        <QueryClientProvider client={client}>
            <PermissionScope permissions={permissions}>
                <MemoryRouter>
                    <LayoutSlotsProvider>
                        <Banner />
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
    it('loads the user providers and the bot detections of the domain', async () => {
        renderPage(editor);

        await waitFor(() => expect(listUserIdentityProviders).toHaveBeenCalledWith(ref));
        expect(listBotDetections).toHaveBeenCalledWith(ref);
    });

    it('saves the stored settings in seconds, without the custom form keys and the bot plugin that are off', async () => {
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

    it('applies the MFA and remember me defaults when they are turned on', async () => {
        vi.mocked(patchDomainAccountSettings).mockResolvedValueOnce(domain);
        renderPage(editor);

        fireEvent.click(screen.getByRole('switch', { name: 'MFA brute force detection' }));
        fireEvent.click(screen.getByRole('switch', { name: 'Remember me' }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchDomainAccountSettings).toHaveBeenCalledWith(
                ref,
                expect.objectContaining({ mfaChallengeMaxAttempts: 3, mfaChallengeAttemptsResetTime: 60, rememberMeDuration: 1_209_600 }),
            ),
        );
    });

    it('blocks the save while the max login attempts is empty', () => {
        renderPage(editor, stored);

        fireEvent.change(screen.getByRole('spinbutton', { name: 'Max login attempts' }), { target: { value: '' } });

        expect(screen.getByRole('spinbutton', { name: 'Max login attempts' })).toHaveAccessibleDescription('Enter at least 1 attempt.');
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('blocks the save while a duration is empty', () => {
        renderPage(editor, stored);

        fireEvent.change(screen.getByRole('spinbutton', { name: 'Account blocked duration' }), { target: { value: '' } });

        expect(screen.getByRole('spinbutton', { name: 'Account blocked duration' })).toHaveAccessibleDescription(
            'Enter a duration of 1 or more.',
        );
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('needs a field on the custom forgot password form, then saves it', async () => {
        vi.mocked(patchDomainAccountSettings).mockResolvedValueOnce(domain);
        renderPage(editor);

        fireEvent.click(screen.getByRole('switch', { name: 'Forgot password form with custom fields' }));
        expect(screen.getByRole('combobox', { name: 'Field key' })).toHaveAccessibleDescription(
            'Add at least one field to the custom form.',
        );
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

    it('lets domain_settings_create alone edit', () => {
        renderPage(['domain_settings_read', 'domain_settings_create'], stored);

        expect(screen.getByRole('switch', { name: 'Remember me' })).toBeEnabled();
        fireEvent.click(screen.getByRole('switch', { name: 'Remember me' }));
        expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument();
    });

    it('shows a read-only form without domain_settings_create and domain_settings_update', () => {
        renderPage(['domain_settings_read'], stored);

        expect(screen.getByRole('switch', { name: 'Brute force detection' })).toBeDisabled();
        expect(screen.getByRole('spinbutton', { name: 'Max login attempts' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });
});
