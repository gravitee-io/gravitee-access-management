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
import { updateIdentityProvider } from '../../lib/api/identity-providers';
import { listAllRoles } from '../../lib/api/roles';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { ExpressionMapperPanel, type Mapper, type MapperTarget } from './ExpressionMapperPanel';
import { useIdentityProvider } from './IdentityProviderLayout';

/** The Angular `ProviderRolesComponent`, with every role of the domain as the `RolesResolver` loads them. */
export function IdentityProviderRolesPage() {
    const { provider } = useIdentityProvider();
    const { ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const canUpdate = usePermissions().has('domain_identity_provider_update');
    const { data: roles, error } = useQuery({ queryKey: ['roles', ref, 'all'], queryFn: () => listAllRoles(ref) });
    const save = useMutation({
        mutationFn: (roleMapper: Mapper) => updateIdentityProvider(ref, { ...provider, roleMapper }),
        onSuccess: updated => {
            queryClient.setQueryData(['identity-provider', ref, provider.id], updated);
            toast.success('Role mapping updated');
        },
        onError: saveError => toast.error(`Could not update the role mapping. ${saveError.message}`),
    });
    const targets = useMemo<MapperTarget[]>(
        () =>
            (roles ?? []).map(role => ({
                id: role.id,
                name: role.name,
                disabled: role.system === true && role.name.endsWith('_PRIMARY_OWNER'),
            })),
        [roles],
    );

    if (error) {
        return <ErrorPanel description="Could not load the roles." error={error} />;
    }
    if (!roles) {
        return <Skeleton className="h-48 w-full" />;
    }
    return (
        <ExpressionMapperPanel
            noun="role"
            title="Role mappers"
            description={
                <>
                    Role mappers set roles and permissions for this provider’s users, during authentication. Match users with{' '}
                    <code className="font-mono text-xs">attributeName=attributeValue</code> (e.g.{' '}
                    <code className="font-mono text-xs">memberOf=administrators</code>) or expression language.
                </>
            }
            targets={targets}
            mapper={provider.roleMapper ?? {}}
            canUpdate={canUpdate}
            saving={save.isPending}
            onSave={roleMapper => save.mutate(roleMapper)}
        />
    );
}
