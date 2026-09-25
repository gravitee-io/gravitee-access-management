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
import { Spinner } from '@gravitee/graphene-core';
import { useQuery } from '@tanstack/react-query';
import { createContext, use } from 'react';
import { Outlet, useParams } from 'react-router-dom';
import { type Domain, type DomainRef, getDomainByHrid } from '../api/management-api';
import { useCurrentEnvironment } from './environment';
import { useSession } from './session';

interface DomainScopeValue {
    readonly domain: Domain;
    readonly ref: DomainRef;
    /** Absolute path of the domain, e.g. `/environments/dev/domains/acme`. */
    readonly basePath: string;
    /** Absolute path of the domains list. */
    readonly domainsPath: string;
}

const DomainContext = createContext<DomainScopeValue | null>(null);

export function useCurrentDomain(): DomainScopeValue {
    const value = use(DomainContext);
    if (!value) {
        throw new Error('useCurrentDomain() called outside <DomainScope>');
    }
    return value;
}

/** Resolves `:domainHrid` for the pages under a security domain. */
export function DomainScope() {
    const { domainHrid = '' } = useParams();
    const { user } = useSession();
    const environment = useCurrentEnvironment();
    const { data: domain, error } = useQuery({
        queryKey: ['domain', user.org, environment.id, domainHrid],
        queryFn: () => getDomainByHrid(user.org, environment.id, domainHrid),
    });

    if (error) {
        return <p className="text-sm text-destructive">Could not load the domain {domainHrid}.</p>;
    }
    if (!domain) {
        return <Spinner />;
    }
    const ref = { organizationId: user.org, environmentId: environment.id, domainId: domain.id };
    const domainsPath = `/environments/${environment.hrids[0]}/domains`;
    return (
        <DomainContext value={{ domain, ref, domainsPath, basePath: `${domainsPath}/${domain.hrid}` }}>
            <Outlet />
        </DomainContext>
    );
}
