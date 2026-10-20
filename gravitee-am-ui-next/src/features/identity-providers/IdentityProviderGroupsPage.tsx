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
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo } from 'react';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { listGroups } from '../../lib/api/groups';
import { updateIdentityProvider } from '../../lib/api/identity-providers';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { ExpressionMapperPanel, type Mapper, type MapperTarget } from './ExpressionMapperPanel';
import { useIdentityProvider } from './IdentityProviderLayout';

/** The Angular `ProviderGroupsComponent`, with the first 100 groups of the domain as the `GroupsResolver` loads them. */
export function IdentityProviderGroupsPage() {
    const { provider } = useIdentityProvider();
    const { ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const canUpdate = usePermissions().has('domain_identity_provider_update');
    const { data: groups, error } = useQuery({ queryKey: ['groups', ref, 1, 100, ''], queryFn: () => listGroups(ref, 0, 100) });
    const save = useMutation({
        mutationFn: (groupMapper: Mapper) => updateIdentityProvider(ref, { ...provider, groupMapper }),
        onSuccess: updated => {
            queryClient.setQueryData(['identity-provider', ref, provider.id], updated);
            toast.success('Group mapping updated');
        },
        onError: saveError => toast.error(`Could not update the group mapping. ${saveError.message}`),
    });
    const targets = useMemo<MapperTarget[]>(() => (groups?.data ?? []).map(group => ({ id: group.id, name: group.name })), [groups]);

    if (error) {
        return <ErrorPanel description="Could not load the groups." error={error} />;
    }
    if (!groups) {
        return <Skeleton className="h-48 w-full" />;
    }
    return (
        <ExpressionMapperPanel
            noun="group"
            title="Group mappers"
            description={
                <>
                    Group mappers put this provider’s users into groups, during authentication. Match users with{' '}
                    <code className="font-mono text-xs">attributeName=attributeValue</code> (e.g.{' '}
                    <code className="font-mono text-xs">memberOf=administrators</code>) or expression language.
                </>
            }
            targets={targets}
            mapper={provider.groupMapper ?? {}}
            canUpdate={canUpdate}
            saving={save.isPending}
            onSave={groupMapper => save.mutate(groupMapper)}
        />
    );
}
