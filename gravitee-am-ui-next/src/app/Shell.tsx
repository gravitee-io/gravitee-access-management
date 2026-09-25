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
import { Outlet, useMatch, useNavigate } from 'react-router-dom';
import { appConfig } from '../config/app-config';
import { listGammaModules } from '../lib/api/gamma-api';
import type { CurrentUser } from '../lib/api/management-api';
import { useCurrentEnvironment } from '../lib/session/environment';
import { useSession } from '../lib/session/session';
import { AM_APP_KEY, buildApps, HOME_APP_KEY } from './gamma-apps';
import { DOMAIN_NAV_GROUPS, NAV_GROUPS } from './navigation';

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

    const envHrid = environment.hrids[0];
    const envBase = `/environments/${envHrid}`;
    const domainBase = domainMatch && `${envBase}/domains/${domainMatch.params.domainHrid}`;
    const navGroups = domainBase ? DOMAIN_NAV_GROUPS : NAV_GROUPS;
    const activeItemKey = domainBase ? domainMatch.params.section : 'domains';
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
    const selectItem = (key: string) => navigate(domainBase && key !== 'domains' ? `${domainBase}/${key}` : `${envBase}/${key}`);

    return (
        <AppLayout
            defaultSidebarMode="hover-expand"
            defaultTheme="system"
            fullHeight
            sidebar={
                <AppSidebar
                    onLogoClick={() => navigate(envBase)}
                    renderNavigation={() => (
                        <SidebarNavigation groups={navGroups} activeItemKey={activeItemKey} onItemSelect={selectItem} />
                    )}
                />
            }
            subheader={
                <ContentHeader
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
