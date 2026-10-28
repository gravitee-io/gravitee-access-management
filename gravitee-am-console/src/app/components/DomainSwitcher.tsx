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
    Command,
    CommandEmpty,
    CommandGroup,
    CommandInput,
    CommandItem,
    CommandList,
    CommandSeparator,
    Popover,
    PopoverContent,
    PopoverTrigger,
    SidebarGroup,
    SidebarMenu,
    SidebarMenuButton,
    SidebarMenuItem,
    useSidebar,
} from '@gravitee/graphene-core';
import { CheckIcon, ChevronsUpDownIcon, GlobeIcon, ListIcon } from '@gravitee/graphene-core/icons';
import { useState } from 'react';
import type { Domain } from '../../lib/api/domains';

interface DomainSwitcherProps {
    readonly domains: readonly Domain[];
    readonly currentDomain: Domain | undefined;
    readonly loading: boolean;
    readonly onSelect: (domain: Domain) => void;
    readonly onManage: () => void;
}

export function DomainSwitcher({ domains, currentDomain, loading, onSelect, onManage }: DomainSwitcherProps) {
    const { state, isMobile } = useSidebar();
    const [open, setOpen] = useState(false);
    const label = currentDomain?.name ?? (loading ? 'Loading domains…' : 'No domain selected');

    return (
        <SidebarGroup className="z-10">
            <SidebarMenu>
                <SidebarMenuItem>
                    <Popover open={open} onOpenChange={setOpen}>
                        <PopoverTrigger asChild>
                            <SidebarMenuButton
                                size="lg"
                                tooltip={state === 'collapsed' ? label : undefined}
                                aria-label={`Switch domain, current ${label}`}
                            >
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
                        </PopoverTrigger>
                        <PopoverContent className="w-64 p-0" align="start" side={isMobile ? 'bottom' : 'right'}>
                            <Command label="Find a domain">
                                <CommandInput placeholder="Find a domain…" />
                                <CommandList>
                                    <CommandEmpty>{domains.length === 0 ? 'No domains yet' : 'No domains match your search'}</CommandEmpty>
                                    {domains.length > 0 && (
                                        <CommandGroup heading="Domains">
                                            {domains.map(domain => (
                                                <CommandItem
                                                    key={domain.id}
                                                    value={domain.id}
                                                    keywords={[domain.name]}
                                                    onSelect={() => {
                                                        setOpen(false);
                                                        onSelect(domain);
                                                    }}
                                                >
                                                    <span className="min-w-0 flex-1 truncate">{domain.name}</span>
                                                    {!domain.enabled && <span className="text-xs text-muted-foreground">Disabled</span>}
                                                    {domain.id === currentDomain?.id && (
                                                        <CheckIcon className="size-4" aria-label="Current domain" />
                                                    )}
                                                </CommandItem>
                                            ))}
                                        </CommandGroup>
                                    )}
                                    <CommandSeparator />
                                    <CommandGroup forceMount>
                                        <CommandItem
                                            value="manage-domains"
                                            forceMount
                                            onSelect={() => {
                                                setOpen(false);
                                                onManage();
                                            }}
                                        >
                                            <ListIcon />
                                            Manage domains
                                        </CommandItem>
                                    </CommandGroup>
                                </CommandList>
                            </Command>
                        </PopoverContent>
                    </Popover>
                </SidebarMenuItem>
            </SidebarMenu>
        </SidebarGroup>
    );
}
