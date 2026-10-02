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
    Badge,
    buildLinearBreadcrumbs,
    ContextSidebar,
    ContextToggleButton,
    type LinearBreadcrumbSegment,
    type NavGroup,
    useLayoutConfig,
} from '@gravitee/graphene-core';
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { navItems } from '../../app/navigation';

interface SectionSidebarOptions {
    readonly title: string;
    /** Short labels under the title, e.g. an application's type and status. */
    readonly badges?: readonly string[];
    readonly groups: NavGroup[];
    /** The absolute path the item keys are relative to. */
    readonly base: string;
    /** Breadcrumbs before the active item's own label. */
    readonly crumbs: readonly LinearBreadcrumbSegment[];
}

/**
 * Shows a settings area's menu in the context sidebar. It also sets the breadcrumbs, except below an item
 * (e.g. one identity provider), where the detail page sets its own.
 */
export function useSectionSidebar({ title, badges = [], groups, base, crumbs }: SectionSidebarOptions) {
    const { pathname } = useLocation();
    const navigate = useNavigate();
    const [expanded, setExpanded] = useState(true);
    // The longest key that prefixes the path wins, so `settings/general` beats `settings`.
    const rest = pathname.slice(base.length + 1);
    const active = navItems(groups)
        .filter(item => rest === item.key || rest.startsWith(`${item.key}/`))
        .sort((a, b) => b.key.length - a.key.length)[0];
    const activeKey = active?.key ?? '';
    const activeLabel = active?.title;
    const detailOwnsBreadcrumbs = !!active && rest !== active.key;

    useLayoutConfig(
        {
            viewMode: 'context',
            contextExpanded: expanded,
            ...(detailOwnsBreadcrumbs
                ? {}
                : { breadcrumbs: buildLinearBreadcrumbs(navigate, [...crumbs, ...(activeLabel ? [{ label: activeLabel }] : [])]) }),
            contextSidebar: (
                <ContextSidebar
                    header={
                        <div className="space-y-2 border-b px-3 pt-4 pb-4">
                            <p className="truncate text-sm font-semibold" title={title}>
                                {title}
                            </p>
                            {badges.length > 0 && (
                                <div className="flex flex-wrap gap-1.5">
                                    {badges.map(badge => (
                                        <Badge key={badge} variant="outline">
                                            {badge}
                                        </Badge>
                                    ))}
                                </div>
                            )}
                        </div>
                    }
                    groups={groups}
                    activeItemKey={activeKey}
                    onItemSelect={key => navigate(`${base}/${key}`)}
                />
            ),
            leading: <ContextToggleButton expanded={expanded} onToggle={() => setExpanded(value => !value)} />,
        },
        [activeKey, activeLabel, base, detailOwnsBreadcrumbs, expanded, title, JSON.stringify(badges), JSON.stringify(crumbs)],
    );
}
