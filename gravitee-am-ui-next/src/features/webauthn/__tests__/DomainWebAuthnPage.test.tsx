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
import type * as Domains from '../../../lib/api/domains';
import { type Domain, listDomainEntrypoints, patchDomainWebAuthnSettings } from '../../../lib/api/domains';
import { PermissionScope } from '../../../lib/session/permissions';
import { DomainWebAuthnPage } from '../DomainWebAuthnPage';

const domain: Domain = { id: 'd1', hrid: 'acme', name: 'Acme', enabled: true, path: '/acme' };
const stored: Domain = {
    ...domain,
    webAuthnSettings: {
        origin: 'https://login.acme.example',
        relyingPartyId: 'acme.example',
        userVerification: 'required',
        certificates: { packed: 'MIIBpacked', tpm: 'MIIBtpm' },
    },
};
const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };
const session = vi.hoisted(() => ({ domain: {} as Domain }));

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<typeof Graphene>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/domains', async importOriginal => ({
    ...(await importOriginal<typeof Domains>()),
    listDomainEntrypoints: vi.fn(),
    patchDomainWebAuthnSettings: vi.fn(),
}));
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
                            <DomainWebAuthnPage />
                        </TooltipProvider>
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </PermissionScope>
        </QueryClientProvider>,
    );
}

const editor = ['domain_settings_read', 'domain_settings_update'];

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listDomainEntrypoints).mockResolvedValue([
        { id: 'default', url: 'https://gw.example', defaultEntrypoint: true },
        { id: 'eu', url: 'https://eu.gw.example', defaultEntrypoint: false },
    ]);
});

describe('DomainWebAuthnPage', () => {
    it('defaults the origin and the RP ID to the access URL of the domain entrypoint, then saves', async () => {
        vi.mocked(patchDomainWebAuthnSettings).mockResolvedValueOnce(domain);
        renderPage(editor);

        expect(await screen.findByRole('textbox', { name: 'Origin' })).toHaveValue('https://eu.gw.example');
        expect(screen.getByRole('textbox', { name: 'Relying party ID' })).toHaveValue('eu.gw.example');
        fireEvent.click(screen.getByRole('switch', { name: 'Force registration' }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchDomainWebAuthnSettings).toHaveBeenCalledWith(ref, {
                origin: 'https://eu.gw.example',
                relyingPartyId: 'eu.gw.example',
                forceRegistration: true,
                certificates: {},
            }),
        );
        expect(toast.success).toHaveBeenCalledWith('WebAuthn configuration updated');
    });

    it('saves the current certificates as a map, without the removed one', async () => {
        vi.mocked(patchDomainWebAuthnSettings).mockResolvedValueOnce(stored);
        renderPage(editor, stored);

        fireEvent.click(await screen.findByRole('button', { name: 'Remove tpm certificate' }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchDomainWebAuthnSettings).toHaveBeenCalledWith(ref, {
                origin: 'https://login.acme.example',
                relyingPartyId: 'acme.example',
                userVerification: 'required',
                certificates: { packed: 'MIIBpacked' },
            }),
        );
    });

    it('adds a certificate and rejects a type that is already in the list', async () => {
        vi.mocked(patchDomainWebAuthnSettings).mockResolvedValueOnce(stored);
        renderPage(editor, stored);

        const addCertificate = async (name: string, value: string) => {
            fireEvent.click(screen.getByRole('combobox', { name: 'Type' }));
            fireEvent.click(await screen.findByRole('option', { name }));
            fireEvent.change(screen.getByRole('textbox', { name: 'Certificate' }), { target: { value } });
            fireEvent.click(screen.getByRole('button', { name: 'Add' }));
        };
        await screen.findByRole('button', { name: 'Remove packed certificate' });
        await addCertificate('packed', 'MIIBother');
        expect(toast.error).toHaveBeenCalledWith('Error : metadata "packed" already exists');

        await addCertificate('apple', 'MIIBapple');
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchDomainWebAuthnSettings).toHaveBeenCalledWith(
                ref,
                expect.objectContaining({ certificates: { packed: 'MIIBpacked', tpm: 'MIIBtpm', apple: 'MIIBapple' } }),
            ),
        );
    });

    it('edits a certificate in place and rejects a name that another row has', async () => {
        vi.mocked(patchDomainWebAuthnSettings).mockResolvedValueOnce(stored);
        renderPage(editor, stored);

        fireEvent.click(await screen.findByRole('button', { name: 'Edit tpm certificate' }));
        fireEvent.change(screen.getByRole('textbox', { name: 'Edited attestation name' }), { target: { value: 'packed' } });
        fireEvent.keyDown(screen.getByRole('textbox', { name: 'Edited attestation name' }), { key: 'Enter' });
        expect(toast.error).toHaveBeenCalledWith('Error : attestation "packed" already exists');

        fireEvent.change(screen.getByRole('textbox', { name: 'Edited attestation name' }), { target: { value: 'apple' } });
        fireEvent.change(screen.getByRole('textbox', { name: 'Edited certificate' }), { target: { value: 'MIIBapple' } });
        fireEvent.keyDown(screen.getByRole('textbox', { name: 'Edited certificate' }), { key: 'Enter' });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchDomainWebAuthnSettings).toHaveBeenCalledWith(
                ref,
                expect.objectContaining({ certificates: { packed: 'MIIBpacked', apple: 'MIIBapple' } }),
            ),
        );
    });

    it('blocks the save while the origin is not an http or https URL', async () => {
        renderPage(editor, stored);

        fireEvent.change(await screen.findByRole('textbox', { name: 'Origin' }), { target: { value: 'login.acme.example' } });

        expect(screen.getByText('The origin must start with http:// or https://.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('blocks the save while the integrity max age is negative', async () => {
        renderPage(editor, stored);

        fireEvent.change(await screen.findByRole('spinbutton', { name: 'Max age of last check (seconds)' }), { target: { value: '-1' } });

        expect(screen.getByText('Enter a number of seconds, 0 or more.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('shows a read-only form without domain_settings_update', async () => {
        renderPage(['domain_settings_read'], stored);

        expect(await screen.findByRole('textbox', { name: 'Origin' })).toBeDisabled();
        expect(screen.getByText('MIIBpacked')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Remove packed certificate' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });
});
