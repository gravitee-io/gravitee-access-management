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
    Badge,
    Button,
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    Empty,
    EmptyDescription,
    EmptyHeader,
    EmptyMedia,
    EmptyTitle,
    Skeleton,
    Switch,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
    toast,
} from '@gravitee/graphene-core';
import { ArrowDownIcon, ArrowUpIcon, FilterIcon, GripVerticalIcon, UsersIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useRef, useState } from 'react';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import { type Application, patchApplication } from '../../lib/api/applications';
import { type IdentityProviderSummary, listIdentityProviderPlugins, listIdentityProviders } from '../../lib/api/identity-providers';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { applicationQueryKey, useApplication } from './ApplicationContext';
import { SelectionRuleDialog } from './SelectionRuleDialog';
import { defaultSelectionRule, moveRow, type ProviderRow, toIdentityProviderLinks, toProviderRows } from './idp-rows';

const FORM_ID = 'application-identity-providers';

interface ProviderLists {
    readonly internal: readonly ProviderRow[];
    readonly external: readonly ProviderRow[];
}

type ListName = keyof ProviderLists;

function toLists(providers: readonly IdentityProviderSummary[], application: Application): ProviderLists {
    const links = application.identityProviders ?? [];
    return {
        internal: toProviderRows(
            providers.filter(provider => !provider.external),
            links,
        ),
        external: toProviderRows(
            providers.filter(provider => provider.external),
            links,
        ),
    };
}

export function ApplicationIdentityProvidersPage() {
    const { ref } = useCurrentDomain();
    const { application } = useApplication();
    const providers = useQuery({ queryKey: ['identity-providers', ref], queryFn: () => listIdentityProviders(ref) });
    const plugins = useQuery({ queryKey: ['identity-provider-plugins'], queryFn: listIdentityProviderPlugins, staleTime: Infinity });

    let content;
    if (providers.error) {
        content = <ErrorPanel description="Could not load the identity providers." error={providers.error} />;
    } else if (!providers.data) {
        content = <Skeleton className="h-32 w-full" />;
    } else if (providers.data.length === 0) {
        content = (
            <Empty className="border border-border">
                <EmptyHeader>
                    <EmptyMedia variant="icon">
                        <UsersIcon />
                    </EmptyMedia>
                    <EmptyTitle>No identity providers</EmptyTitle>
                    <EmptyDescription>
                        No identity provider has been set up in this domain yet. Add one under Settings › Identity providers, then choose
                        which ones this application uses.
                    </EmptyDescription>
                </EmptyHeader>
            </Empty>
        );
    } else {
        const typeName = (type: string) => {
            const plugin = plugins.data?.find(candidate => candidate.id === type);
            return plugin ? (plugin.displayName ?? plugin.name) : 'Custom';
        };
        content = (
            <ProvidersForm
                key={JSON.stringify(application.identityProviders ?? [])}
                initial={toLists(providers.data, application)}
                typeName={typeName}
            />
        );
    }

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Identity providers"
                description="The identity sources that authenticate this application's users and supply their profile."
            />
            {content}
        </div>
    );
}

interface ProvidersFormProps {
    readonly initial: ProviderLists;
    readonly typeName: (type: string) => string;
}

function ProvidersForm({ initial, typeName }: ProvidersFormProps) {
    const { ref } = useCurrentDomain();
    const { application } = useApplication();
    const queryClient = useQueryClient();
    // Without the read right the API leaves the links out, so a save would unlink every provider.
    const canEdit = usePermissions().has('application_identity_provider_read', 'application_identity_provider_update');
    const [saved, setSaved] = useState(initial);
    const [lists, setLists] = useState(initial);
    const [editing, setEditing] = useState<{ list: ListName; row: ProviderRow } | null>(null);
    const dirty = JSON.stringify(lists) !== JSON.stringify(saved);

    const save = useMutation({
        mutationFn: () =>
            patchApplication(ref, application.id, { identityProviders: toIdentityProviderLinks(lists.internal, lists.external) }),
        onSuccess: updated => {
            setSaved(lists);
            queryClient.setQueryData(applicationQueryKey(ref, application.id), updated);
            toast.success('Application updated');
        },
        onError: error => toast.error(`Could not update the application. ${error.message}`),
    });

    const update = (list: ListName, rows: readonly ProviderRow[]) => setLists(current => ({ ...current, [list]: rows }));
    const openRule = (list: ListName, row: ProviderRow) => {
        if (canEdit || row.selectionRule) {
            setEditing({ list, row });
        }
    };
    const applyRule = (rule: string) => {
        if (editing) {
            const { list, row } = editing;
            update(
                list,
                lists[list].map(candidate => (candidate.id === row.id ? { ...candidate, selectionRule: rule, selected: true } : candidate)),
            );
        }
        setEditing(null);
    };
    const submit = (event: FormEvent) => {
        event.preventDefault();
        save.mutate();
    };

    return (
        <form id={FORM_ID} onSubmit={submit} className="flex flex-col gap-6">
            {lists.internal.length > 0 && (
                <ProviderTable
                    title="Identity providers"
                    description="Directories and databases that hold the users' credentials. Users are tried in priority order."
                    rows={lists.internal}
                    typeName={typeName}
                    canEdit={canEdit}
                    onChange={rows => update('internal', rows)}
                    onEditRule={row => openRule('internal', row)}
                />
            )}
            {lists.external.length > 0 && (
                <ProviderTable
                    title="Social and enterprise providers"
                    description="External providers shown as sign-in options on the login page."
                    rows={lists.external}
                    typeName={typeName}
                    canEdit={canEdit}
                    onChange={rows => update('external', rows)}
                    onEditRule={row => openRule('external', row)}
                />
            )}
            {canEdit && <SaveBar formId={FORM_ID} dirty={dirty} saving={save.isPending} onDiscard={() => setLists(saved)} />}
            {editing && (
                <SelectionRuleDialog
                    providerName={editing.row.name}
                    rule={editing.row.selectionRule || (canEdit ? defaultSelectionRule(editing.row.type) : '')}
                    readOnly={!canEdit}
                    onApply={applyRule}
                    onClose={() => setEditing(null)}
                />
            )}
        </form>
    );
}

interface ProviderTableProps {
    readonly title: string;
    readonly description: string;
    readonly rows: readonly ProviderRow[];
    readonly typeName: (type: string) => string;
    readonly canEdit: boolean;
    readonly onChange: (rows: readonly ProviderRow[]) => void;
    readonly onEditRule: (row: ProviderRow) => void;
}

function ProviderTable({ title, description, rows, typeName, canEdit, onChange, onEditRule }: ProviderTableProps) {
    const dragged = useRef<number | null>(null);
    const [announcement, setAnnouncement] = useState('');
    const move = (from: number, to: number) => {
        if (from !== to) {
            onChange(moveRow(rows, from, to));
            setAnnouncement(`${rows[from]?.name} moved to priority ${to + 1} of ${rows.length}`);
        }
    };

    return (
        <Card>
            <CardHeader>
                <CardTitle>{title}</CardTitle>
                <CardDescription>{description}</CardDescription>
            </CardHeader>
            <CardContent>
                <p aria-live="polite" className="sr-only">
                    {announcement}
                </p>
                <Table aria-label={title}>
                    <TableHeader>
                        <TableRow>
                            <TableHead className="w-28">Priority</TableHead>
                            <TableHead>Name</TableHead>
                            <TableHead>Type</TableHead>
                            <TableHead>Selection rule</TableHead>
                            <TableHead>Selected</TableHead>
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        {rows.map((row, index) => (
                            <TableRow
                                key={row.id}
                                draggable={canEdit}
                                onDragStart={event => {
                                    dragged.current = index;
                                    event.dataTransfer.effectAllowed = 'move';
                                }}
                                onDragOver={event => {
                                    if (dragged.current !== null) {
                                        event.preventDefault();
                                    }
                                }}
                                onDrop={event => {
                                    event.preventDefault();
                                    if (dragged.current !== null) {
                                        move(dragged.current, index);
                                    }
                                    dragged.current = null;
                                }}
                                onDragEnd={() => {
                                    dragged.current = null;
                                }}
                            >
                                <TableCell>
                                    <div className="flex items-center gap-1">
                                        {canEdit && <GripVerticalIcon aria-hidden className="size-4 cursor-grab text-muted-foreground" />}
                                        <span className="w-4 text-sm tabular-nums">{index + 1}</span>
                                        {canEdit && (
                                            <>
                                                <Button
                                                    type="button"
                                                    variant="ghost"
                                                    size="icon-xs"
                                                    aria-label={`Move ${row.name} up`}
                                                    disabled={index === 0}
                                                    onClick={() => move(index, index - 1)}
                                                >
                                                    <ArrowUpIcon aria-hidden />
                                                </Button>
                                                <Button
                                                    type="button"
                                                    variant="ghost"
                                                    size="icon-xs"
                                                    aria-label={`Move ${row.name} down`}
                                                    disabled={index === rows.length - 1}
                                                    onClick={() => move(index, index + 1)}
                                                >
                                                    <ArrowDownIcon aria-hidden />
                                                </Button>
                                            </>
                                        )}
                                    </div>
                                </TableCell>
                                <TableCell className="font-medium">{row.name}</TableCell>
                                <TableCell>
                                    <Badge variant="outline">{typeName(row.type)}</Badge>
                                </TableCell>
                                <TableCell>
                                    {row.selectionRule || canEdit ? (
                                        <Button
                                            type="button"
                                            variant="ghost"
                                            size="sm"
                                            aria-label={`${row.selectionRule ? 'Selection rule' : 'New selection rule'} of ${row.name}`}
                                            onClick={() => onEditRule(row)}
                                        >
                                            <FilterIcon aria-hidden />
                                            {row.selectionRule ? 'Rule set' : 'Add rule'}
                                        </Button>
                                    ) : (
                                        <span className="text-sm text-muted-foreground">None</span>
                                    )}
                                </TableCell>
                                <TableCell>
                                    <Switch
                                        aria-label={`Use ${row.name}`}
                                        checked={row.selected}
                                        disabled={!canEdit}
                                        onCheckedChange={selected =>
                                            onChange(
                                                rows.map(candidate => (candidate.id === row.id ? { ...candidate, selected } : candidate)),
                                            )
                                        }
                                    />
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </CardContent>
        </Card>
    );
}
