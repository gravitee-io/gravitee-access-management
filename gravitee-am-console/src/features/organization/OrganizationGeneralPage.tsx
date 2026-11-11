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
import { Skeleton, toast } from '@gravitee/graphene-core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { getOrganizationSettings, listOrganizationIdentityProviders } from '../../lib/api/organization';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useSession } from '../../lib/session/session';
import { AuthenticationForm } from './AuthenticationForm';
import { useOrganizationPath } from './useOrganizationPath';

export function OrganizationGeneralPage() {
    const { user } = useSession();
    const queryClient = useQueryClient();
    const base = useOrganizationPath();
    useBreadcrumbs([{ label: 'Organization', to: `${base}/general` }, { label: 'Authentication' }]);
    const settings = useQuery({ queryKey: ['organization-settings', user.org], queryFn: () => getOrganizationSettings(user.org) });
    const providers = useQuery({
        queryKey: ['organization-identity-providers', user.org],
        queryFn: () => listOrganizationIdentityProviders(user.org),
    });

    const error = settings.error ?? providers.error;
    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Authentication"
                description="The platform preferences let you configure how your users will connect to the Access Management Admin Portal."
            />
            {error ? (
                <ErrorPanel
                    description="Could not load the authentication settings."
                    error={error}
                    onRetry={() => void Promise.all([settings.refetch(), providers.refetch()])}
                />
            ) : !settings.data || !providers.data ? (
                <Skeleton className="h-72 w-full" />
            ) : (
                <AuthenticationForm
                    key={String(settings.dataUpdatedAt)}
                    organizationId={user.org}
                    settings={settings.data}
                    providers={providers.data}
                    onSaved={updated => {
                        queryClient.setQueryData(['organization-settings', user.org], updated);
                        toast.success('Settings updated');
                    }}
                />
            )}
        </div>
    );
}
