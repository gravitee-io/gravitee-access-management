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
import { usePermissions } from '../../lib/session/permissions';
import { useApplication } from './ApplicationContext';

/** The settings pages in the order the index opens the first one the user may read. */
const SETTINGS_LANDING: readonly { readonly path: string; readonly permission: string }[] = [
    { path: 'general', permission: 'application_settings_read' },
    { path: 'oauth2', permission: 'application_openid_read' },
    { path: 'members', permission: 'application_member_list' },
    { path: 'secrets-certificates', permission: 'application_certificate_list' },
    { path: 'factors', permission: 'application_factor_list' },
];

export function ApplicationSettingsLayout() {
    const { applicationPath } = useApplication();
    const { pathname } = useLocation();
    const { has } = usePermissions();
    if (pathname.replace(/\/$/, '') === `${applicationPath}/settings`) {
        const landing = SETTINGS_LANDING.find(page => has(page.permission));
        return <Navigate to={landing ? landing.path : `${applicationPath}/overview`} replace />;
    }
    return <Outlet />;
}
