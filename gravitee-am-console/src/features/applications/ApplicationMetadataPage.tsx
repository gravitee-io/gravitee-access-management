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
    Field,
    FieldError,
    FieldLabel,
    Input,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
    toast,
} from '@gravitee/graphene-core';
import { PlusIcon, XIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, type KeyboardEvent, useId, useRef, useState } from 'react';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import { type Application, patchApplication } from '../../lib/api/applications';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { applicationQueryKey, useApplication } from './ApplicationContext';
import { type MetadataEntry, metadataErrors, toEntries, toMetadata } from './metadata-entries';

const FORM_ID = 'application-metadata-form';

const ignoreEnter = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
        event.preventDefault();
    }
};

export function ApplicationMetadataPage() {
    const { application } = useApplication();
    const { ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const canEdit = usePermissions().has('application_settings_update');
    const [entries, setEntries] = useState<readonly MetadataEntry[]>(() => toEntries(application.metadata));
    const [draft, setDraft] = useState({ name: '', value: '' });
    const newEntryPrefix = useId();
    const newEntryCount = useRef(0);
    const [touched, setTouched] = useState<ReadonlySet<string>>(new Set());

    const [syncedApplication, setSyncedApplication] = useState<Application>(application);
    if (syncedApplication !== application) {
        setSyncedApplication(application);
        setEntries(toEntries(application.metadata));
        setTouched(new Set());
    }

    const save = useMutation({
        mutationFn: () => patchApplication(ref, application.id, { metadata: toMetadata(entries) }),
        onSuccess: updated => {
            queryClient.setQueryData(applicationQueryKey(ref, application.id), updated);
            toast.success('Application updated');
        },
        onError: error => toast.error(`Could not update the application. ${error.message}`),
    });

    const initial = toEntries(application.metadata);
    const dirty =
        JSON.stringify(entries.map(({ name, value }) => [name, value])) !== JSON.stringify(initial.map(({ name, value }) => [name, value]));
    const errors = metadataErrors(entries);
    const update = (id: string, patch: Partial<MetadataEntry>) =>
        setEntries(current => current.map(entry => (entry.id === id ? { ...entry, ...patch } : entry)));
    const add = () => {
        const name = draft.name.trim();
        if (entries.some(entry => entry.name === name)) {
            toast.error(`Error : metadata "${name}" already exists`);
            return;
        }
        setEntries(current => [...current, { id: `${newEntryPrefix}${newEntryCount.current++}`, name, value: draft.value }]);
        setDraft({ name: '', value: '' });
    };
    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (errors.size === 0) {
            save.mutate();
        }
    };

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Metadata"
                description={
                    <>
                        Custom information about the application, available in the execution context as{' '}
                        <span className="font-mono">client.metadata</span>.
                    </>
                }
            />
            <form id={FORM_ID} onSubmit={submit}>
                <Card>
                    <CardHeader>
                        <CardTitle>Metadata</CardTitle>
                        <CardDescription>Name and value pairs attached to this application.</CardDescription>
                    </CardHeader>
                    <CardContent className="flex flex-col gap-4">
                        {canEdit && (
                            <div className="flex flex-wrap items-end gap-2 rounded-md border border-border p-3">
                                <Field className="w-64">
                                    <FieldLabel htmlFor="metadata-name">Name</FieldLabel>
                                    <Input
                                        id="metadata-name"
                                        value={draft.name}
                                        onKeyDown={ignoreEnter}
                                        onChange={event => setDraft({ ...draft, name: event.target.value })}
                                    />
                                </Field>
                                <Field className="min-w-0 flex-1">
                                    <FieldLabel htmlFor="metadata-value">Value</FieldLabel>
                                    <Input
                                        id="metadata-value"
                                        value={draft.value}
                                        onKeyDown={ignoreEnter}
                                        onChange={event => setDraft({ ...draft, value: event.target.value })}
                                    />
                                </Field>
                                <Button type="button" variant="outline" disabled={!draft.name.trim() || !draft.value} onClick={add}>
                                    <PlusIcon aria-hidden />
                                    Add
                                </Button>
                            </div>
                        )}
                        {entries.length === 0 ? (
                            <p className="py-4 text-center text-sm text-muted-foreground">There is no metadata.</p>
                        ) : (
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead>Name</TableHead>
                                        <TableHead>Value</TableHead>
                                        <TableHead>
                                            <span className="sr-only">Actions</span>
                                        </TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {entries.map(entry => {
                                        const error = touched.has(entry.id) ? errors.get(entry.id) : undefined;
                                        const errorId = `metadata-${entry.id}-error`;
                                        return (
                                            <TableRow key={entry.id}>
                                                <TableCell className="w-64 align-top">
                                                    {canEdit ? (
                                                        <Field data-invalid={!!error}>
                                                            <Input
                                                                aria-label="Metadata name"
                                                                className="h-8"
                                                                value={entry.name}
                                                                aria-invalid={!!error}
                                                                aria-describedby={error ? errorId : undefined}
                                                                onKeyDown={ignoreEnter}
                                                                onChange={event => update(entry.id, { name: event.target.value })}
                                                                onBlur={() => setTouched(current => new Set(current).add(entry.id))}
                                                            />
                                                            {error && <FieldError id={errorId}>{error}</FieldError>}
                                                        </Field>
                                                    ) : (
                                                        <span className="font-medium">{entry.name}</span>
                                                    )}
                                                </TableCell>
                                                <TableCell className="align-top">
                                                    {canEdit ? (
                                                        <Input
                                                            aria-label={`Value of ${entry.name}`}
                                                            className="h-8"
                                                            value={entry.value}
                                                            onKeyDown={ignoreEnter}
                                                            onChange={event =>
                                                                update(entry.id, { value: event.target.value, raw: undefined })
                                                            }
                                                        />
                                                    ) : (
                                                        entry.value
                                                    )}
                                                </TableCell>
                                                <TableCell className="text-right align-top">
                                                    {canEdit && (
                                                        <Button
                                                            type="button"
                                                            variant="ghost"
                                                            size="icon-xs"
                                                            aria-label={`Remove ${entry.name}`}
                                                            onClick={() =>
                                                                setEntries(current => current.filter(other => other.id !== entry.id))
                                                            }
                                                        >
                                                            <XIcon aria-hidden />
                                                        </Button>
                                                    )}
                                                </TableCell>
                                            </TableRow>
                                        );
                                    })}
                                </TableBody>
                            </Table>
                        )}
                    </CardContent>
                </Card>
            </form>
            {canEdit && (
                <SaveBar
                    formId={FORM_ID}
                    dirty={dirty}
                    saving={save.isPending}
                    invalid={errors.size > 0}
                    onDiscard={() => {
                        setEntries(toEntries(application.metadata));
                        setTouched(new Set());
                    }}
                />
            )}
        </div>
    );
}
