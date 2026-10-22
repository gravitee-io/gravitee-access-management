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
import { MembershipsPanel } from '../../app/components/MembershipsPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { addDomainMember, listDomainMembers, removeDomainMember } from '../../lib/api/domains';
import { toMembers } from '../../lib/api/memberships';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';

/**
 * The Angular `DomainSettingsMembershipsComponent` (Settings › Administrative roles): the console users and groups who
 * may manage this security domain, and with which DOMAIN role.
 */
export function DomainMembersPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const { has } = usePermissions();
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Administrative Roles' },
    ]);

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Administrative Roles"
                description="Who can manage this security domain in the console, and with which role."
            />
            <MembershipsPanel
                queryKey={['domain-members', ref]}
                listMembers={async () => toMembers(await listDomainMembers(ref))}
                saveMember={member => addDomainMember(ref, member)}
                removeMember={membershipId => removeDomainMember(ref, membershipId)}
                roleType="DOMAIN"
                resourceLabel="security domain"
                canCreate={has('domain_member_create')}
                canUpdate={has('domain_member_update')}
                canDelete={has('domain_member_delete')}
            />
        </div>
    );
}
