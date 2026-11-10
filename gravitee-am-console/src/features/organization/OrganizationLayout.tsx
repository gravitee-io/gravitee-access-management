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
import { Outlet } from 'react-router-dom';
import { ORGANIZATION_SETTINGS_NAV_GROUPS, permittedNavGroups } from '../../app/navigation';
import { useSectionSidebar } from '../../lib/layout/useSectionSidebar';
import { usePermissions } from '../../lib/session/permissions';
import { isManaged, useInstallationConfiguration } from './useCloudMode';
import { useOrganizationPath } from './useOrganizationPath';

/** The sidebar reads its groups once, so the layout waits for the installation type, which hides the Cockpit item in cloud mode. */
export function OrganizationLayout() {
    const installation = useInstallationConfiguration();
    if (installation.isPending) {
        return <Spinner />;
    }
    return <OrganizationSidebar cloudMode={isManaged(installation.data)} />;
}

function OrganizationSidebar({ cloudMode }: { readonly cloudMode: boolean }) {
    const base = useOrganizationPath();
    const { hasAny } = usePermissions();
    useSectionSidebar({
        title: 'Organization settings',
        groups: permittedNavGroups(ORGANIZATION_SETTINGS_NAV_GROUPS, hasAny, cloudMode),
        base,
        crumbs: [{ label: 'Organization', to: `${base}/general` }],
    });
    return <Outlet />;
}
