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
import { applicationSectionAccess } from '../../app/navigation';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { useApplication } from './ApplicationContext';

/** Guards a section with the rules of its menu entry: a missing permission shows Not allowed, a section the application or the domain does not have opens the overview. */
export function RequireApplicationSection({ section }: { readonly section: string }) {
    const { application, applicationPath } = useApplication();
    const { domain } = useCurrentDomain();
    const { hasAny } = usePermissions();
    const access = applicationSectionAccess(section, { type: application.type, samlEnabled: !!domain.saml?.enabled, hasAny });
    if (access === 'absent') {
        return <Navigate to={`${applicationPath}/overview`} replace />;
    }
    return access === 'forbidden' ? <ForbiddenPanel /> : <Outlet />;
}
