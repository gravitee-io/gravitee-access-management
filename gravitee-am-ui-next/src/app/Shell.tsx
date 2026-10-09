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
    AppContextBar,
    AppLayout,
    AppSidebar,
    ContentHeader,
    SidebarNavigation,
    TopNavUser,
    useLayoutSlots,
} from '@gravitee/graphene-core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Outlet, useMatch, useNavigate } from 'react-router-dom';
import { appConfig } from '../config/app-config';
import { listGammaModules } from '../lib/api/gamma-api';
import { type CurrentUser, listDomains } from '../lib/api/management-api';
import { useCurrentEnvironment } from '../lib/session/environment';
import { useSession } from '../lib/session/session';
import { DomainSwitcher } from './components/DomainSwitcher';
import { AM_APP_KEY, buildApps, HOME_APP_KEY } from './gamma-apps';
import { MAIN_NAV_GROUPS, ORGANIZATION_KEY } from './navigation';

function displayName(user: CurrentUser): string {
    return user.name ?? ([user.given_name, user.family_name].filter(Boolean).join(' ') || user.preferred_username || user.sub);
}

function initials(user: CurrentUser): string | undefined {
    const letters = [user.given_name, user.family_name].map(part => part?.charAt(0) ?? '').join('');
    return letters.length === 2 ? letters.toUpperCase() : undefined;
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
    const { data: domainPage, isPending: domainsLoading } = useQuery({
        queryKey: ['domains', user.org, environment.id, 'switcher'],
        queryFn: () => listDomains(user.org, environment.id, 0, 50),
    });
    const domains = domainPage?.data ?? [];

    // The menu acts on the domain in the URL, else the last one visited, else the first.
    const urlDomainHrid = domainMatch?.params.domainHrid;
    const [lastDomainHrid, setLastDomainHrid] = useState<string>();
    if (urlDomainHrid && urlDomainHrid !== lastDomainHrid) {
        setLastDomainHrid(urlDomainHrid);
    }
    const currentDomainHrid = urlDomainHrid ?? lastDomainHrid ?? domains[0]?.hrid;
    const currentDomain = domains.find(domain => domain.hrid === currentDomainHrid);
    const activeItemKey = organizationMatch ? ORGANIZATION_KEY : urlDomainHrid && (domainMatch?.params.section ?? 'dashboard');
    const { gamma } = appConfig();
    const { data: gammaModules } = useQuery({
        queryKey: ['gamma-modules'],
        queryFn: () => listGammaModules(gamma!),
        enabled: !!gamma,
        staleTime: Infinity,
    });
    const apps = buildApps(!!gamma, gammaModules);
    // Leaves AM for gamma-console: Home, or the module's own route there.
    const switchApp = (key: string) => {
        if (gamma && key !== AM_APP_KEY) {
            const target = key === HOME_APP_KEY ? 'home' : key;
            window.location.assign(`${gamma.consoleUrl}/environments/${gamma.environmentHrid}/${target}`);
        }
    };
    const selectItem = (key: string) => {
        if (key === ORGANIZATION_KEY) {
            navigate(`${envBase}/organization`);
        } else {
            navigate(currentDomainHrid ? `${domainsPath}/${currentDomainHrid}/${key}` : domainsPath);
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
                                // Stays on the same section, as AM does.
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
                            apps={apps}
                            activeAppKey={AM_APP_KEY}
                            onAppChange={switchApp}
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
