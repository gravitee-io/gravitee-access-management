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
import { Badge, Button, Card, CardContent, CardDescription, CardHeader, CardTitle, Skeleton, toast } from '@gravitee/graphene-core';
import { ArrowLeftIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { NotFoundPanel } from '../../app/components/NotFoundPanel';
import { SaveBar } from '../../app/components/SaveBar';
import {
    deleteOrganizationEntrypoint,
    getOrganizationEntrypoint,
    type OrganizationEntrypoint,
    updateOrganizationEntrypoint,
} from '../../lib/api/entrypoints';
import { isNotFound } from '../../lib/api/http';
import { listTags } from '../../lib/api/tags';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { usePermissions } from '../../lib/session/permissions';
import { useSession } from '../../lib/session/session';
import { EntrypointFields, type EntrypointDraft, isEntrypointValid } from './EntrypointFields';
import { useCloudMode } from './useCloudMode';
import { useOrganizationPath } from './useOrganizationPath';

const FORM_ID = 'entrypoint-form';

function fromEntrypoint(entrypoint: OrganizationEntrypoint): EntrypointDraft {
    return { name: entrypoint.name, description: entrypoint.description ?? '', url: entrypoint.url, tags: entrypoint.tags ?? [] };
}

export function OrganizationEntrypointPage() {
    const { entrypointId = '' } = useParams();
    const { user } = useSession();
    const base = useOrganizationPath();
    const listPath = `${base}/entrypoints`;
    const entrypoint = useQuery({
        queryKey: ['organization-entrypoint', user.org, entrypointId],
        queryFn: () => getOrganizationEntrypoint(user.org, entrypointId),
        retry: false,
    });
    const tags = useQuery({ queryKey: ['tags', user.org], queryFn: () => listTags(user.org) });
    useBreadcrumbs([
        { label: 'Organization', to: `${base}/general` },
        { label: 'Entrypoints', to: listPath },
        { label: entrypoint.data?.name ?? entrypointId },
    ]);

    const error = entrypoint.error ?? tags.error;
    if (error) {
        return isNotFound(error) ? (
            <NotFoundPanel
                title="Entrypoint not found"
                description="This entrypoint does not exist. It may have been deleted."
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to entrypoints</Link>
                    </Button>
                }
            />
        ) : (
            <ErrorPanel description="Could not load the entrypoint." error={error} />
        );
    }
    if (!entrypoint.data || !tags.data) {
        return <Skeleton className="h-72 w-full" />;
    }
    return <EntrypointDetail key={entrypoint.data.id} entrypoint={entrypoint.data} tags={tags.data} listPath={listPath} />;
}

interface EntrypointDetailProps {
    readonly entrypoint: OrganizationEntrypoint;
    readonly tags: Awaited<ReturnType<typeof listTags>>;
    readonly listPath: string;
}

function EntrypointDetail({ entrypoint, tags, listPath }: EntrypointDetailProps) {
    const { user } = useSession();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const { has } = usePermissions();
    const cloudMode = useCloudMode();
    const readOnly = !has('organization_entrypoint_update') || cloudMode;
    const canDelete = !cloudMode && !entrypoint.defaultEntrypoint && has('organization_entrypoint_delete');
    const [draft, setDraft] = useState<EntrypointDraft>(() => fromEntrypoint(entrypoint));
    const [confirmOpen, setConfirmOpen] = useState(false);
    const save = useMutation({
        mutationFn: () =>
            updateOrganizationEntrypoint(user.org, entrypoint.id, {
                name: draft.name,
                description: draft.description,
                url: draft.url,
                tags: [...draft.tags],
            }),
        onSuccess: updated => {
            queryClient.setQueryData(['organization-entrypoint', user.org, entrypoint.id], updated);
            void queryClient.invalidateQueries({ queryKey: ['organization-entrypoints', user.org] });
            setDraft(fromEntrypoint(updated));
            toast.success('Entrypoint updated');
        },
        onError: error => toast.error(`Could not update the entrypoint. ${error.message}`),
    });
    const remove = useMutation({
        mutationFn: () => deleteOrganizationEntrypoint(user.org, entrypoint.id),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['organization-entrypoints', user.org] });
            toast.success('Entrypoint deleted');
            navigate(listPath);
        },
        onError: error => toast.error(`Could not delete the entrypoint. ${error.message}`),
    });

    const dirty = JSON.stringify(draft) !== JSON.stringify(fromEntrypoint(entrypoint));
    const valid = isEntrypointValid(draft);
    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (valid) {
            save.mutate();
        }
    };

    return (
        <div className="flex flex-col gap-6">
            <div className="space-y-1">
                <Button asChild variant="ghost" size="sm" className="-ml-2">
                    <Link to={listPath}>
                        <ArrowLeftIcon aria-hidden />
                        Entrypoints
                    </Link>
                </Button>
                <div className="flex items-center gap-2">
                    <h1>{entrypoint.name}</h1>
                    {entrypoint.defaultEntrypoint && <Badge variant="secondary">Default</Badge>}
                </div>
            </div>

            <form id={FORM_ID} className="flex flex-col gap-6" onSubmit={submit}>
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Details</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <EntrypointFields
                            draft={draft}
                            tags={tags}
                            readOnly={readOnly}
                            lockIdentity={entrypoint.defaultEntrypoint}
                            onChange={patch => setDraft(current => ({ ...current, ...patch }))}
                        />
                    </CardContent>
                </Card>
            </form>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Manage entrypoint</CardTitle>
                    <CardDescription>
                        An entrypoint determines which url your end-users will have to use to contact the AM Gateway. Entrypoints are
                        associated with tags. When a tag is also associated to a domain, the corresponding entrypoint will be automatically
                        selected to display the right url to use to contact the Gateway.
                    </CardDescription>
                </CardHeader>
                <CardContent className="text-sm text-muted-foreground">
                    So, if you associate one of the following tags [ {draft.tags.length > 0 ? draft.tags.join(', ') : 'No tag defined'} ] to
                    a domain <code>my-domain</code>, domain users will access to AM Gateway through the following url{' '}
                    <code>{draft.url}/my-domain</code>
                </CardContent>
            </Card>

            {!readOnly && (
                <SaveBar
                    formId={FORM_ID}
                    dirty={dirty}
                    saving={save.isPending}
                    invalid={!valid}
                    onDiscard={() => setDraft(fromEntrypoint(entrypoint))}
                />
            )}

            {canDelete && (
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Danger zone</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="flex flex-wrap items-center justify-between gap-4">
                            <div className="flex flex-1 flex-col gap-0.5">
                                <p className="text-sm font-medium">Delete entrypoint</p>
                                <p className="text-sm text-muted-foreground">
                                    Entrypoint will be deleted and will not be used for gateway deployment. This operation cannot be undone.
                                </p>
                            </div>
                            <Button variant="destructive" disabled={remove.isPending} onClick={() => setConfirmOpen(true)}>
                                Delete
                            </Button>
                        </div>
                    </CardContent>
                </Card>
            )}

            <ConfirmDialog
                open={confirmOpen}
                title="Delete Entrypoint"
                description="Are you sure you want to delete this entrypoint ?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={setConfirmOpen}
                onConfirm={() => remove.mutate()}
            />
        </div>
    );
}
