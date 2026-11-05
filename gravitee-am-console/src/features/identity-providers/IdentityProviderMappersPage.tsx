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
    FieldError,
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
import { type FormEvent, useMemo, useState } from 'react';
import { SaveBar } from '../../app/components/SaveBar';
import { updateIdentityProvider } from '../../lib/api/identity-providers';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { useIdentityProvider } from './IdentityProviderLayout';

const FORM_ID = 'identity-provider-mappers';

interface Mapping {
    /** Identifies the row while its key is edited. */
    readonly id: number;
    readonly key: string;
    readonly value: string;
}

type Column = 'key' | 'value';

/** The Angular `defaultMappers`, shown until the provider has mappers of its own. */
const DEFAULT_MAPPERS: Record<string, string> = {
    sub: 'uid',
    email: 'mail',
    name: 'displayname',
    given_name: 'givenname',
    family_name: 'sn',
};

let nextId = 0;
const newRow = (key = '', value = ''): Mapping => ({ id: nextId++, key, value });
const toRows = (mappers: Record<string, string>): Mapping[] => Object.entries(mappers).map(([key, value]) => newRow(key, value));
const toMappers = (rows: readonly Mapping[]) => Object.fromEntries(rows.map(row => [row.key.trim(), row.value.trim()]));
const contentOf = (rows: readonly Mapping[]) => JSON.stringify(rows.map(row => [row.key.trim(), row.value.trim()]));

/** Binds user profile attributes to the raw attributes or expressions of the provider. */
export function IdentityProviderMappersPage() {
    const { provider } = useIdentityProvider();
    const { ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const canUpdate = usePermissions().has('domain_identity_provider_update');
    const initial = useMemo(() => toRows(provider.mappers ?? DEFAULT_MAPPERS), [provider.mappers]);
    const [rows, setRows] = useState<Mapping[]>(initial);
    const [touched, setTouched] = useState<ReadonlySet<string>>(new Set());
    const save = useMutation({
        mutationFn: (mappers: Record<string, string>) => updateIdentityProvider(ref, { ...provider, mappers }),
        onSuccess: updated => {
            queryClient.setQueryData(['identity-provider', ref, provider.id], updated);
            toast.success('Mappers saved');
        },
        onError: error => toast.error(`Could not save the mappers. ${error.message}`),
    });

    const dirty = contentOf(rows) !== contentOf(initial);
    const keys = rows.map(row => row.key.trim());
    const errorOf = (row: Mapping, index: number, column: Column): string | undefined => {
        if (column === 'key' && row.key.trim() && keys.indexOf(row.key.trim()) !== index) {
            return 'This attribute is already mapped.';
        }
        return touched.has(`${row.id}:${column}`) && !row[column].trim() ? 'Required.' : undefined;
    };
    const invalid = rows.some((row, index) => !row.key.trim() || !row.value.trim() || errorOf(row, index, 'key') !== undefined);

    const update = (id: number, patch: Partial<Mapping>) =>
        setRows(current => current.map(row => (row.id === id ? { ...row, ...patch } : row)));
    const touch = (id: number, column: Column) => setTouched(current => new Set(current).add(`${id}:${column}`));
    const discard = () => {
        setRows(initial);
        setTouched(new Set());
    };

    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (!invalid) {
            save.mutate(toMappers(rows));
        }
    };
    const addRow = () => setRows(current => [...current, newRow()]);

    return (
        <form id={FORM_ID} noValidate onSubmit={submit} className="flex flex-col gap-6">
            <Card>
                <CardHeader>
                    <CardTitle>User mappers</CardTitle>
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
                                    <Button type="button" size="sm" onClick={addRow}>
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
                                    <TableHead>
                                        <span className="sr-only">Actions</span>
                                    </TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {rows.map((row, index) => (
                                    <TableRow key={row.id}>
                                        {(['key', 'value'] as const).map(column => {
                                            const error = errorOf(row, index, column);
                                            const errorId = `mapper-${row.id}-${column}-error`;
                                            return (
                                                <TableCell key={column} className="align-top">
                                                    <Input
                                                        aria-label={column === 'key' ? 'User profile attribute' : 'Raw attribute'}
                                                        placeholder={column === 'key' ? 'User attribute' : 'Raw attribute. Supports EL.'}
                                                        className={column === 'value' ? 'font-mono' : undefined}
                                                        value={row[column]}
                                                        disabled={!canUpdate}
                                                        aria-invalid={error !== undefined}
                                                        aria-describedby={error ? errorId : undefined}
                                                        onChange={event => update(row.id, { [column]: event.target.value })}
                                                        onBlur={() => touch(row.id, column)}
                                                    />
                                                    {error && <FieldError id={errorId} className="mt-1" errors={[{ message: error }]} />}
                                                </TableCell>
                                            );
                                        })}
                                        <TableCell className="w-12 align-top">
                                            {canUpdate && (
                                                <Button
                                                    type="button"
                                                    variant="ghost"
                                                    size="icon"
                                                    aria-label={`Remove ${row.key || 'mapper'}`}
                                                    onClick={() => setRows(current => current.filter(other => other.id !== row.id))}
                                                >
                                                    <Trash2Icon />
                                                </Button>
                                            )}
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    )}
                </CardContent>
            </Card>

            {canUpdate && rows.length > 0 && (
                <Button type="button" variant="outline" className="self-start" onClick={addRow}>
                    <PlusIcon />
                    Add mapper
                </Button>
            )}
            {canUpdate && <SaveBar formId={FORM_ID} dirty={dirty} saving={save.isPending} invalid={invalid} onDiscard={discard} />}
        </form>
    );
}
