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
import { SidebarProvider } from '@gravitee/graphene-core';
import { buttonHarness, renderWithGraphene } from '@gravitee/graphene-core/testing';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { Domain } from '../../../lib/api/domains';
import { DomainSwitcher } from '../DomainSwitcher';

const domains: Domain[] = [
    { id: 'd1', hrid: 'one', name: 'One', enabled: true },
    { id: 'd2', hrid: 'two', name: 'Two', enabled: false },
];

interface HarnessProps {
    readonly currentDomain?: Domain;
    readonly domains?: Domain[];
}

function setupDomainSwitcherHarness({ currentDomain, domains: list = domains }: HarnessProps = {}) {
    const onSelect = vi.fn();
    const onManage = vi.fn();
    renderWithGraphene(
        <SidebarProvider>
            <DomainSwitcher domains={list} currentDomain={currentDomain} loading={false} onSelect={onSelect} onManage={onManage} />
        </SidebarProvider>,
    );
    return {
        onSelect,
        onManage,
        open: () => buttonHarness({ name: /^Switch domain/ }).click(),
        search: (text: string) => userEvent.type(screen.getByRole('combobox', { name: 'Find a domain' }), text),
        option: (name: string) => screen.queryByRole('option', { name: new RegExp(`^${name}`) }),
        pick: (name: string) => userEvent.click(screen.getByRole('option', { name: new RegExp(`^${name}`) })),
        manage: () => userEvent.click(screen.getByRole('option', { name: 'Manage domains' })),
    };
}

describe('DomainSwitcher', () => {
    it('names the current domain on the trigger', () => {
        setupDomainSwitcherHarness({ currentDomain: domains[0] });

        expect(screen.getByRole('button', { name: 'Switch domain, current One' })).toBeInTheDocument();
    });

    it('marks the current domain and flags a disabled domain', async () => {
        const harness = setupDomainSwitcherHarness({ currentDomain: domains[0] });

        await harness.open();

        expect(harness.option('One')).toContainElement(screen.getByLabelText('Current domain'));
        expect(harness.option('Two')).toHaveTextContent('Disabled');
    });

    it('selects a domain and opens the domain list', async () => {
        const harness = setupDomainSwitcherHarness({ currentDomain: domains[0] });

        await harness.open();
        await harness.pick('Two');
        await harness.open();
        await harness.manage();

        expect(harness.onSelect).toHaveBeenCalledWith(domains[1]);
        expect(harness.onManage).toHaveBeenCalledOnce();
    });

    it('finds a domain by name and keeps Manage domains', async () => {
        const harness = setupDomainSwitcherHarness();

        await harness.open();
        await harness.search('tw');

        expect(harness.option('Two')).toBeInTheDocument();
        expect(harness.option('One')).not.toBeInTheDocument();
        expect(screen.getByRole('option', { name: 'Manage domains' })).toBeInTheDocument();

        await harness.search('zzz');
        expect(screen.getByText('No domains match your search')).toBeInTheDocument();
    });

    it('says when there is no domain yet', async () => {
        const harness = setupDomainSwitcherHarness({ domains: [] });

        expect(screen.getByRole('button', { name: 'Switch domain, current No domain selected' })).toBeInTheDocument();
        await harness.open();
        expect(screen.getByText('No domains yet')).toBeInTheDocument();
    });
});
