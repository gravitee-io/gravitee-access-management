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
import { Button, Card, CardContent, CardHeader, CardTitle, PageFocused, Skeleton, toast } from '@gravitee/graphene-core';
import { ArrowLeftIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { NotFoundPanel } from '../../app/components/NotFoundPanel';
import { isNotFound } from '../../lib/api/http';
import { deleteTrustedDomain, getTrustedDomain, updateTrustedDomain } from '../../lib/api/trust-domains';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { TrustDomainForm } from './TrustDomainForm';
import { fromTrustedDomain, type TrustDomain, toTrustedDomainRequest } from './trust-domain';

/** The Angular `TrustDomainComponent`: the trusted domain form in edit mode, and the deletion of the trusted domain. */
export function TrustDomainPage() {
    const { trustDomainId = '' } = useParams();
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const { has } = usePermissions();
    const listPath = `${basePath}/settings/trust-domains/domains`;
    const [confirming, setConfirming] = useState(false);
    const query = useQuery({ queryKey: ['trusted-domain', ref, trustDomainId], queryFn: () => getTrustedDomain(ref, trustDomainId) });
    const trustDomain = useMemo(() => query.data && fromTrustedDomain(query.data), [query.data]);
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Trusted Domains', to: listPath },
        { label: trustDomain?.name ?? '…' },
    ]);

    const save = useMutation({
        mutationFn: (payload: TrustDomain) => updateTrustedDomain(ref, trustDomainId, toTrustedDomainRequest(payload)),
        onSuccess: updated => {
            queryClient.setQueryData(['trusted-domain', ref, trustDomainId], updated);
            void queryClient.invalidateQueries({ queryKey: ['trusted-domains', ref] });
            toast.success('Trusted domain updated');
        },
        onError: error => toast.error(error.message || 'Failed to update trusted domain'),
    });
    const remove = useMutation({
        mutationFn: () => deleteTrustedDomain(ref, trustDomainId),
        onSuccess: () => {
            setConfirming(false);
            queryClient.removeQueries({ queryKey: ['trusted-domain', ref, trustDomainId] });
            void queryClient.invalidateQueries({ queryKey: ['trusted-domains', ref] });
            toast.success('Trusted domain deleted');
            navigate(listPath);
        },
        onError: () => toast.error('Failed to delete trusted domain'),
    });

    if (query.error) {
        return isNotFound(query.error) ? (
            <NotFoundPanel
                title="Trusted domain not found"
                description={`No trusted domain with this id exists in ${domain.name}. It may have been deleted.`}
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to trusted domains</Link>
                    </Button>
                }
            />
        ) : (
            <ErrorPanel description="Could not load the trusted domain." error={query.error} />
        );
    }
    if (!trustDomain) {
        return <Skeleton className="h-64 w-full" />;
    }

    return (
        <PageFocused>
            <div className="space-y-6">
                <div className="space-y-1">
                    <Button asChild variant="ghost" size="sm" className="-ml-2">
                        <Link to={listPath}>
                            <ArrowLeftIcon aria-hidden />
                            Trusted domains
                        </Link>
                    </Button>
                    <h1>{trustDomain.name}</h1>
                    {trustDomain.description ? <p className="text-sm text-muted-foreground">{trustDomain.description}</p> : null}
                </div>

                <TrustDomainForm
                    key={`${query.data?.id}-${query.data?.updatedAt}`}
                    trustDomain={trustDomain}
                    createMode={false}
                    editMode={has('domain_trust_domain_update')}
                    submitLabel="Save"
                    onSubmit={payload => save.mutateAsync(payload)}
                />

                {has('domain_trust_domain_delete') ? (
                    <Card className="border-destructive/30">
                        <CardHeader>
                            <CardTitle className="text-base">Danger zone</CardTitle>
                        </CardHeader>
                        <CardContent>
                            <div className="flex flex-wrap items-center justify-between gap-4">
                                <div className="flex flex-1 flex-col gap-0.5">
                                    <p className="text-sm font-medium">Delete trusted domain</p>
                                    <p className="text-sm text-muted-foreground">
                                        Deleting a trusted domain stops AM believing anything it vouches for.
                                    </p>
                                </div>
                                <Button variant="destructive" disabled={remove.isPending} onClick={() => setConfirming(true)}>
                                    Delete
                                </Button>
                            </div>
                        </CardContent>
                    </Card>
                ) : null}
            </div>

            <ConfirmDialog
                open={confirming}
                title="Delete Trusted Domain"
                description={`Are you sure you want to delete "${trustDomain.name}"?`}
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={setConfirming}
                onConfirm={() => remove.mutate()}
            />
        </PageFocused>
    );
}
