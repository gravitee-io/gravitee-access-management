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
import { Button, PageFocused, toast } from '@gravitee/graphene-core';
import { ArrowLeftIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { createTrustedDomain } from '../../lib/api/trust-domains';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { TrustDomainForm } from './TrustDomainForm';
import { DEFAULT_REFRESH_INTERVAL_SECONDS, type TrustDomain, toTrustedDomainRequest } from './trust-domain';

const INITIAL: TrustDomain = { name: '', refreshIntervalSeconds: DEFAULT_REFRESH_INTERVAL_SECONDS };

/** The Angular `TrustDomainCreationComponent`: the trusted domain form in create mode, which opens the new trusted domain. */
export function NewTrustDomainPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const listPath = `${basePath}/settings/trust-domains/domains`;
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Trusted Domains', to: listPath },
        { label: 'New' },
    ]);

    const create = useMutation({
        mutationFn: (payload: TrustDomain) => createTrustedDomain(ref, toTrustedDomainRequest(payload)),
        onSuccess: created => {
            queryClient.setQueryData(['trusted-domain', ref, created.id], created);
            void queryClient.invalidateQueries({ queryKey: ['trusted-domains', ref] });
            toast.success('Trusted domain created');
            navigate(`${listPath}/${created.id}`, { replace: true });
        },
        onError: error => toast.error(error.message || 'Failed to create trusted domain'),
    });

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
                    <h1>Add a trusted domain</h1>
                    <p className="text-sm text-muted-foreground">
                        An external authority whose tokens or workload identities this domain accepts, or that it issues Cross App Access
                        tokens towards.
                    </p>
                </div>
                <TrustDomainForm
                    trustDomain={INITIAL}
                    createMode
                    editMode
                    submitLabel="Create"
                    cancel={
                        <Button asChild variant="outline">
                            <Link to={listPath}>Cancel</Link>
                        </Button>
                    }
                    onSubmit={payload => create.mutateAsync(payload)}
                />
            </div>
        </PageFocused>
    );
}
