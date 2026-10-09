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
import { Outlet } from 'react-router-dom';
import { DOMAIN_SETTINGS_NAV_GROUPS } from '../../app/navigation';
import { useSectionSidebar } from '../../lib/layout/useSectionSidebar';
import { useCurrentDomain } from '../../lib/session/domain';

export function DomainSettingsLayout() {
    const { domain, basePath, domainsPath } = useCurrentDomain();
    const base = `${basePath}/settings`;
    useSectionSidebar({
        title: 'Domain settings',
        groups: DOMAIN_SETTINGS_NAV_GROUPS,
        base,
        crumbs: [
            { label: 'Domains', to: domainsPath },
            { label: domain.name, to: `${basePath}/dashboard` },
            { label: 'Settings', to: `${base}/general` },
        ],
    });
    return <Outlet />;
}
