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
import type { NavGroup } from '@gravitee/graphene-core';
import {
    AppWindowIcon,
    BellIcon,
    BotIcon,
    BoxesIcon,
    LayoutDashboardIcon,
    SettingsIcon,
    ShieldCheckIcon,
    UserCogIcon,
} from '@gravitee/graphene-core/icons';

export const ORGANIZATION_KEY = 'organization';

export const MAIN_NAV_GROUPS: NavGroup[] = [
    {
        label: 'Domain',
        items: [
            { key: 'dashboard', title: 'Dashboard', icon: LayoutDashboardIcon },
            { key: 'applications', title: 'Applications', icon: AppWindowIcon },
            { key: 'agents', title: 'Agents', icon: BotIcon },
            { key: 'mcp-servers', title: 'MCP Servers', icon: BoxesIcon },
            { key: 'authorization', title: 'Authorization', icon: ShieldCheckIcon },
            { key: 'alerts', title: 'Alerts', icon: BellIcon },
            { key: 'settings', title: 'Settings', icon: SettingsIcon },
        ],
    },
    {
        label: 'Organization',
        items: [{ key: ORGANIZATION_KEY, title: 'Organization', icon: UserCogIcon }],
    },
];

export function navItems(groups: NavGroup[]) {
    return groups.flatMap(group => group.items);
}
