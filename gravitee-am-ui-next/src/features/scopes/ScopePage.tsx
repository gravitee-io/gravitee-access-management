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
import { Badge, Button, Card, CardContent, CardHeader, CardTitle, PageFocused, Skeleton, toast } from '@gravitee/graphene-core';
import { ArrowLeftIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { NotFoundPanel } from '../../app/components/NotFoundPanel';
import { SaveBar } from '../../app/components/SaveBar';
import { isNotFound } from '../../lib/api/http';
import { deleteScope, getScope, type Scope, updateScope } from '../../lib/api/scopes';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { Can, usePermissions } from '../../lib/session/permissions';
import { ScopeFields, type ScopeFormValues } from './ScopeFields';
import { consentDraftInvalid, draftSeconds } from './consent';

function formValues(scope: Scope): ScopeFormValues {
    return {
        key: scope.key,
        name: scope.name,
        description: scope.description,
        discovery: scope.discovery,
        parameterized: scope.parameterized,
        amount: '',
        unit: '',
        clearedExpiry: false,
    };
}

interface ScopeFormProps {
    readonly scope: Scope;
    readonly listPath: string;
}

function ScopeForm({ scope, listPath }: ScopeFormProps) {
    const { ref } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const canUpdate = usePermissions().has('domain_scope_update');
    const [confirmDelete, setConfirmDelete] = useState(false);
    const form = useForm<ScopeFormValues>({ mode: 'onChange', defaultValues: formValues(scope) });
    const { isDirty, isValid, errors } = form.formState;
    const [draftAmount, draftUnit] = useWatch({ control: form.control, name: ['amount', 'unit'] });
    const consentInvalid = consentDraftInvalid({ amount: draftAmount, unit: draftUnit });

    const save = useMutation({
        // The Angular `ScopeService.update` sends these five fields. A cleared period goes as null.
        mutationFn: (values: ScopeFormValues) =>
            updateScope(ref, scope.id, {
                name: values.name,
                description: values.description,
                expiresIn: draftSeconds(values) ?? (values.clearedExpiry ? null : scope.expiresIn),
                discovery: values.discovery,
                parameterized: values.parameterized,
            }),
        onSuccess: updated => {
            queryClient.setQueryData(['scope', ref, scope.id], updated);
            void queryClient.invalidateQueries({ queryKey: ['scopes', ref] });
            form.reset(formValues(updated));
            toast.success('Scope updated');
        },
        onError: error => toast.error(`Could not update the scope. ${error.message}`),
    });
    const remove = useMutation({
        mutationFn: () => deleteScope(ref, scope.id),
        onSuccess: () => {
            queryClient.removeQueries({ queryKey: ['scope', ref, scope.id] });
            void queryClient.invalidateQueries({ queryKey: ['scopes', ref] });
            toast.success('Scope deleted');
            navigate(listPath);
        },
        onError: error => {
            setConfirmDelete(false);
            toast.error(`Could not delete the scope. ${error.message}`);
        },
    });

    return (
        <form noValidate onSubmit={form.handleSubmit(values => save.mutate(values))} className="flex flex-col gap-6">
            <ScopeFields
                control={form.control}
                register={form.register}
                errors={errors}
                keyReadOnly
                canEdit={canUpdate}
                system={scope.system}
                storedExpiresIn={scope.expiresIn}
                onClearExpiry={() => form.setValue('clearedExpiry', true, { shouldDirty: true })}
            />
            <SaveBar
                canEdit={canUpdate}
                dirty={isDirty}
                saving={save.isPending}
                invalid={!isValid || consentInvalid}
                onDiscard={() => form.reset(formValues(scope))}
            />

            {!scope.system && (
                <Can all={['domain_scope_delete']}>
                    <Card className="border-destructive/30">
                        <CardHeader>
                            <CardTitle className="text-base">Danger zone</CardTitle>
                        </CardHeader>
                        <CardContent>
                            <div className="flex flex-wrap items-center justify-between gap-4">
                                <div className="flex flex-col gap-0.5">
                                    <p className="text-sm font-medium">Delete scope</p>
                                    <p className="text-sm text-muted-foreground">
                                        Scope will be deleted, clients and users will not be able to use this scope as permission access.
                                        This operation cannot be undone.
                                    </p>
                                </div>
                                <Button type="button" variant="destructive" onClick={() => setConfirmDelete(true)}>
                                    Delete
                                </Button>
                            </div>
                        </CardContent>
                    </Card>
                </Can>
            )}

            <ConfirmDialog
                open={confirmDelete}
                title="Delete Scope"
                description="Are you sure you want to delete this scope ?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={setConfirmDelete}
                onConfirm={() => remove.mutate()}
            />
        </form>
    );
}

/** The Angular `ScopeComponent`: the key is fixed, the rest is saved together. A system scope keeps its switches and cannot be deleted. */
export function ScopePage() {
    const { scopeId = '' } = useParams();
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const listPath = `${basePath}/settings/scopes`;
    const { data: scope, error } = useQuery({ queryKey: ['scope', ref, scopeId], queryFn: () => getScope(ref, scopeId) });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Scopes', to: listPath },
        { label: scope?.name ?? '…' },
    ]);

    if (error) {
        return isNotFound(error) ? (
            <NotFoundPanel
                title="Scope not found"
                description={`No scope with this id exists in ${domain.name}. It may have been deleted.`}
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to scopes</Link>
                    </Button>
                }
            />
        ) : (
            <ErrorPanel description="Could not load the scope." error={error} />
        );
    }
    if (!scope) {
        return <Skeleton className="h-64 w-full" />;
    }

    return (
        <PageFocused>
            <div className="flex flex-col gap-6">
                <div className="flex flex-col gap-1">
                    <Button asChild variant="ghost" size="sm" className="-ml-2 self-start">
                        <Link to={listPath}>
                            <ArrowLeftIcon aria-hidden />
                            Scopes
                        </Link>
                    </Button>
                    <div className="flex flex-wrap items-center gap-2">
                        <h1>{scope.name}</h1>
                        {scope.system && <Badge variant="secondary">System</Badge>}
                    </div>
                    <p className="text-sm text-muted-foreground">
                        Scopes limit access to a resource, for example WRITE access to restricted resources, or READ access to public ones.
                    </p>
                </div>
                <ScopeForm key={`${scope.id}-${scope.updatedAt}`} scope={scope} listPath={listPath} />
            </div>
        </PageFocused>
    );
}
