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
    Button,
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    DataTableEmptyState,
    Input,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
    toast,
} from '@gravitee/graphene-core';
import { ListIcon, PlusIcon, Trash2Icon } from '@gravitee/graphene-core/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { SaveBar } from '../../app/components/SaveBar';
import { updateIdentityProvider } from '../../lib/api/identity-providers';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { useIdentityProvider } from './IdentityProviderLayout';

interface Mapping {
    readonly key: string;
    readonly value: string;
}

/** The Angular `defaultMappers`, shown until the provider has mappers of its own. */
const DEFAULT_MAPPERS: Record<string, string> = {
    sub: 'uid',
    email: 'mail',
    name: 'displayname',
    given_name: 'givenname',
    family_name: 'sn',
};

const toRows = (mappers: Record<string, string>): Mapping[] => Object.entries(mappers).map(([key, value]) => ({ key, value }));

/** The Angular `ProviderMappersComponent`: binds user profile attributes to the provider's raw attributes or expressions. */
export function IdentityProviderMappersPage() {
    const { provider } = useIdentityProvider();
    const { ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const canUpdate = usePermissions().has('domain_identity_provider_update');
    const initial = useMemo(() => toRows(provider.mappers ?? DEFAULT_MAPPERS), [provider.mappers]);
    const [rows, setRows] = useState<Mapping[]>(initial);
    const save = useMutation({
        mutationFn: (mappers: Record<string, string>) => updateIdentityProvider(ref, { ...provider, mappers }),
        onSuccess: updated => {
            queryClient.setQueryData(['identity-provider', ref, provider.id], updated);
            toast.success('Mappers saved');
        },
        onError: error => toast.error(`Could not save the mappers. ${error.message}`),
    });

    const dirty = JSON.stringify(rows) !== JSON.stringify(initial);
    const keys = rows.map(row => row.key.trim());
    const duplicate = keys.find((key, index) => key && keys.indexOf(key) !== index);
    const incomplete = rows.some(row => !row.key.trim() || !row.value.trim());

    const update = (index: number, patch: Partial<Mapping>) =>
        setRows(current => current.map((row, i) => (i === index ? { ...row, ...patch } : row)));

    const submit = () => {
        if (duplicate) {
            toast.error(`Error : attribute ${duplicate} already exists`);
            return;
        }
        save.mutate(Object.fromEntries(rows.map(row => [row.key.trim(), row.value.trim()])));
    };

    return (
        <div className="flex flex-col gap-6">
            <Card>
                <CardHeader>
                    <CardTitle className="text-base">User mappers</CardTitle>
                    <CardDescription>
                        Attribute mapping binds user profile attributes to your identity provider’s attributes, during authentication.
                        Mapped attributes can be read from the UserInfo endpoint or used in ID token claims. Use a provider attribute name,
                        or expression language such as <code className="font-mono text-xs">{"{#profile['my-attr']}"}</code> or{' '}
                        <code className="font-mono text-xs">{"{#jsonPath(#profile, '$.additional-info.attr')}"}</code>.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    {rows.length === 0 ? (
                        <DataTableEmptyState
                            variant="first-use"
                            icon={<ListIcon />}
                            title="No user mappers yet"
                            description="Map user profile attributes to the attributes this provider returns."
                            primaryAction={
                                canUpdate ? (
                                    <Button size="sm" onClick={() => setRows([{ key: '', value: '' }])}>
                                        <PlusIcon />
                                        Add mapper
                                    </Button>
                                ) : undefined
                            }
                        />
                    ) : (
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>User profile attribute</TableHead>
                                    <TableHead>Raw attribute</TableHead>
                                    {canUpdate && (
                                        <TableHead>
                                            <span className="sr-only">Actions</span>
                                        </TableHead>
                                    )}
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {rows.map((row, index) => (
                                    // A row has no identity of its own while it is edited, so the index is its key.
                                    <TableRow key={index}>
                                        <TableCell>
                                            <Input
                                                aria-label="User profile attribute"
                                                placeholder="User attribute"
                                                value={row.key}
                                                disabled={!canUpdate}
                                                onChange={event => update(index, { key: event.target.value })}
                                            />
                                        </TableCell>
                                        <TableCell>
                                            <Input
                                                aria-label="Raw attribute"
                                                placeholder="Raw attribute. Supports EL."
                                                className="font-mono"
                                                value={row.value}
                                                disabled={!canUpdate}
                                                onChange={event => update(index, { value: event.target.value })}
                                            />
                                        </TableCell>
                                        {canUpdate && (
                                            <TableCell className="w-12">
                                                <Button
                                                    variant="ghost"
                                                    size="icon"
                                                    aria-label={`Remove ${row.key || 'mapper'}`}
                                                    onClick={() => setRows(current => current.filter((_, i) => i !== index))}
                                                >
                                                    <Trash2Icon />
                                                </Button>
                                            </TableCell>
                                        )}
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    )}
                </CardContent>
            </Card>

            {canUpdate && (
                <div className="flex items-center justify-between gap-2">
                    {rows.length > 0 ? (
                        <Button variant="outline" onClick={() => setRows(current => [...current, { key: '', value: '' }])}>
                            <PlusIcon />
                            Add mapper
                        </Button>
                    ) : (
                        <span />
                    )}
                    <SaveBar
                        dirty={dirty}
                        saving={save.isPending}
                        invalid={incomplete}
                        onSave={submit}
                        onDiscard={() => setRows(initial)}
                    />
                </div>
            )}
        </div>
    );
}
