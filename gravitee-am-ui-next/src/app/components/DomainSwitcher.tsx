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
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
    SidebarGroup,
    SidebarMenu,
    SidebarMenuButton,
    SidebarMenuItem,
    useSidebar,
} from '@gravitee/graphene-core';
import { CheckIcon, ChevronsUpDownIcon, GlobeIcon, ListIcon } from '@gravitee/graphene-core/icons';
import type { Domain } from '../../lib/api/management-api';

interface DomainSwitcherProps {
    readonly domains: readonly Domain[];
    readonly currentDomain: Domain | undefined;
    readonly loading: boolean;
    readonly onSelect: (domain: Domain) => void;
    readonly onManage: () => void;
}

/** The domain picker at the top of the main sidebar. The domain menu below it acts on the selected domain. */
export function DomainSwitcher({ domains, currentDomain, loading, onSelect, onManage }: DomainSwitcherProps) {
    const { state, isMobile } = useSidebar();
    const label = currentDomain?.name ?? (loading ? 'Loading domains…' : 'No domain selected');

    return (
        <SidebarGroup className="z-10">
            <SidebarMenu>
                <SidebarMenuItem>
                    <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                            <SidebarMenuButton size="lg" tooltip={state === 'collapsed' ? label : undefined} aria-label="Switch domain">
                                <span className="flex size-8 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary">
                                    <GlobeIcon className="size-4" aria-hidden />
                                </span>
                                <span className="grid min-w-0 flex-1 text-left leading-tight">
                                    <span className="truncate text-xs text-muted-foreground">Domains</span>
                                    <span className="truncate text-sm font-medium" title={label}>
                                        {label}
                                    </span>
                                </span>
                                <ChevronsUpDownIcon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                            </SidebarMenuButton>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent className="w-64" align="start" side={isMobile ? 'bottom' : 'right'}>
                            <DropdownMenuLabel className="text-xs text-muted-foreground">Domains</DropdownMenuLabel>
                            {domains.map(domain => (
                                <DropdownMenuItem key={domain.id} onSelect={() => onSelect(domain)}>
                                    <span className="min-w-0 flex-1 truncate">{domain.name}</span>
                                    {!domain.enabled && <span className="text-xs text-muted-foreground">Disabled</span>}
                                    {domain.id === currentDomain?.id && <CheckIcon className="size-4" aria-hidden />}
                                </DropdownMenuItem>
                            ))}
                            {domains.length === 0 && !loading && <DropdownMenuItem disabled>No domains yet</DropdownMenuItem>}
                            <DropdownMenuSeparator />
                            <DropdownMenuItem onSelect={onManage}>
                                <ListIcon />
                                Manage domains
                            </DropdownMenuItem>
                        </DropdownMenuContent>
                    </DropdownMenu>
                </SidebarMenuItem>
            </SidebarMenu>
        </SidebarGroup>
    );
}
