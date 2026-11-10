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
import { Navigate, Outlet } from 'react-router-dom';
import { ForbiddenPanel } from '../../app/components/ForbiddenPanel';
import { APPLICATION_NAV_GROUPS } from '../../app/navigation';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { useApplication } from './ApplicationContext';

const SECTIONS = new Map(APPLICATION_NAV_GROUPS.flatMap(group => group.items).map(item => [item.key, item]));

/** Guards a section with the rules of its menu entry: a missing permission shows Not allowed, a section the application or the domain does not have opens the overview. */
export function RequireApplicationSection({ section }: { readonly section: string }) {
    const { application, applicationPath } = useApplication();
    const { domain } = useCurrentDomain();
    const { hasAny } = usePermissions();
    const item = SECTIONS.get(section);
    if (!item) {
        throw new Error(`No application menu entry for the section ${section}`);
    }
    const type = application.type.toUpperCase();
    if (
        (item.types && !item.types.some(allowed => allowed === type)) ||
        (item.requiresSaml && !domain.saml?.enabled) ||
        (item.requiresAgent && type !== 'AGENT')
    ) {
        return <Navigate to={`${applicationPath}/overview`} replace />;
    }
    return item.perms && !hasAny(...item.perms) ? <ForbiddenPanel /> : <Outlet />;
}
