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
import { Tabs, TabsList, TabsTrigger } from '@gravitee/graphene-core';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { PageHeader } from '../../app/components/PageHeader';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';

/** The Angular `TrustDomainsContainerComponent`: the Trusted Domains title and its Domains and Key retrieval tabs. */
export function TrustDomainsTabs({ active, actions }: Readonly<{ active: 'domains' | 'key-retrieval'; actions?: ReactNode }>) {
    const { basePath } = useCurrentDomain();
    const navigate = useNavigate();
    const { has } = usePermissions();
    return (
        <div className="flex flex-col gap-4">
            <PageHeader
                title="Trusted Domains"
                description="External authorities whose tokens and workload identities this domain trusts, and the ones it issues Cross App Access tokens towards."
                actions={actions}
            />
            <Tabs value={active} onValueChange={tab => navigate(`${basePath}/settings/trust-domains/${tab}`)}>
                <TabsList variant="line">
                    <TabsTrigger value="domains">Domains</TabsTrigger>
                    {has('domain_settings_read') ? <TabsTrigger value="key-retrieval">Key retrieval</TabsTrigger> : null}
                </TabsList>
            </Tabs>
        </div>
    );
}
