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
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { applicationSectionAccess } from '../../app/navigation';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { useApplication } from './ApplicationContext';

/** The settings pages in the Angular order. The index opens the first one the user may open. */
const SETTINGS_LANDING = ['general', 'oauth2', 'members', 'secrets-certificates', 'factors'] as const;

export function ApplicationSettingsLayout() {
    const { application, applicationPath } = useApplication();
    const { domain } = useCurrentDomain();
    const { pathname } = useLocation();
    const { hasAny } = usePermissions();
    if (pathname.replace(/\/$/, '') === `${applicationPath}/settings`) {
        const context = { type: application.type, samlEnabled: !!domain.saml?.enabled, hasAny };
        const landing = SETTINGS_LANDING.find(page => applicationSectionAccess(`settings/${page}`, context) === 'open');
        return <Navigate to={landing ?? `${applicationPath}/overview`} replace />;
    }
    return <Outlet />;
}
