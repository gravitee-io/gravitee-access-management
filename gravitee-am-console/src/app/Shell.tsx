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
import {
    type AppDefinition,
    AppContextBar,
    AppLayout,
    AppSidebar,
    ContentHeader,
    SidebarNavigation,
    TopNavUser,
    useLayoutSlots,
} from '@gravitee/graphene-core';
import { GioIamIcon } from '@gravitee/graphene-core/icons';
import { useState } from 'react';
import { Outlet, useMatch, useNavigate } from 'react-router-dom';
import type { Domain } from '../lib/api/domains';
import type { CurrentUser } from '../lib/api/management-api';
import { useEnvironmentDomains } from '../lib/session/domains';
import { useCurrentEnvironment } from '../lib/session/environment';
import { useUserPreferences } from '../lib/session/preferences';
import { useSession } from '../lib/session/session';
import { DomainSwitcher } from './components/DomainSwitcher';
import { MAIN_NAV_GROUPS, ORGANIZATION_KEY } from './navigation';

const AM_APP_KEY = 'am';
const APPS: AppDefinition[] = [{ key: AM_APP_KEY, label: 'Access Management', icon: <GioIamIcon className="size-5" /> }];

function displayName(user: CurrentUser): string {
    return user.name ?? ([user.given_name, user.family_name].filter(Boolean).join(' ') || user.preferred_username || user.sub);
}

function initials(user: CurrentUser): string | undefined {
    const letters = [user.given_name, user.family_name].map(part => part?.charAt(0) ?? '').join('');
    return letters.length === 2 ? letters.toUpperCase() : undefined;
}

export function pickCurrentDomain(
    domains: readonly Domain[],
    urlDomainHrid: string | undefined,
    lastDomainHrid: string | undefined,
    defaultDomainId: string | null | undefined,
): Domain | undefined {
    const byHrid = (hrid: string | undefined) => domains.find(domain => domain.hrid === hrid);
    if (urlDomainHrid) {
        return byHrid(urlDomainHrid);
    }
    return byHrid(lastDomainHrid) ?? domains.find(domain => domain.id === defaultDomainId) ?? domains[0];
}

export function Shell() {
    const { user, environments } = useSession();
    const environment = useCurrentEnvironment();
    const navigate = useNavigate();
    const { slots } = useLayoutSlots();
    const domainMatch = useMatch('/environments/:envHrid/domains/:domainHrid/:section?/*');
    const organizationMatch = useMatch('/environments/:envHrid/organization/*');

    const envHrid = environment.hrids[0];
    const envBase = `/environments/${envHrid}`;
    const domainsPath = `${envBase}/domains`;

    const { data: domainPage, isPending: domainsLoading } = useEnvironmentDomains();
    const { data: preferences } = useUserPreferences();
    const domains = domainPage?.data ?? [];

    // `domains/new` is the creation page, not a domain.
    const urlDomainHrid = domainMatch?.params.domainHrid === 'new' ? undefined : domainMatch?.params.domainHrid;
    const [lastDomainHrid, setLastDomainHrid] = useState<string>();
    if (urlDomainHrid && urlDomainHrid !== lastDomainHrid) {
        setLastDomainHrid(urlDomainHrid);
    }
    const currentDomain = pickCurrentDomain(domains, urlDomainHrid, lastDomainHrid, preferences?.defaultDomainId);
    const activeItemKey = organizationMatch ? ORGANIZATION_KEY : urlDomainHrid && (domainMatch?.params.section ?? 'dashboard');

    const selectItem = (key: string) => {
        if (key === ORGANIZATION_KEY) {
            navigate(`${envBase}/organization`);
        } else {
            navigate(currentDomain ? `${domainsPath}/${currentDomain.hrid}/${key}` : domainsPath);
        }
    };

    return (
        <AppLayout
            defaultSidebarMode="hover-expand"
            defaultTheme="system"
            fullHeight
            viewMode={slots.viewMode}
            contextExpanded={slots.contextExpanded}
            contextSidebar={slots.contextSidebar}
            sidebar={
                <AppSidebar
                    onLogoClick={() => navigate(envBase)}
                    renderNavigation={() => (
                        <>
                            <DomainSwitcher
                                domains={domains}
                                currentDomain={currentDomain}
                                loading={domainsLoading}
                                onSelect={domain =>
                                    navigate(
                                        `${domainsPath}/${domain.hrid}/${activeItemKey && activeItemKey !== ORGANIZATION_KEY ? activeItemKey : 'dashboard'}`,
                                    )
                                }
                                onManage={() => navigate(domainsPath)}
                            />
                            <SidebarNavigation groups={MAIN_NAV_GROUPS} activeItemKey={activeItemKey} onItemSelect={selectItem} />
                        </>
                    )}
                />
            }
            subheader={
                <ContentHeader
                    leading={slots.leading}
                    breadcrumbs={slots.breadcrumbs}
                    appContext={
                        <AppContextBar
                            apps={APPS}
                            activeAppKey={AM_APP_KEY}
                            environments={environments.map(env => ({ key: env.hrids[0], label: env.name }))}
                            activeEnvironmentKey={envHrid}
                            onEnvironmentChange={key => navigate(`/environments/${key}`)}
                        />
                    }
                    trailing={
                        <TopNavUser
                            name={displayName(user)}
                            initials={initials(user)}
                            email={user.email}
                            onSignOut={() => navigate('/logout')}
                        />
                    }
                />
            }
        >
            <Outlet />
        </AppLayout>
    );
}
