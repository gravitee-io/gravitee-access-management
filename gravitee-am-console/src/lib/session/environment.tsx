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
import { createContext, use } from 'react';
import { Navigate, Outlet, useParams } from 'react-router-dom';
import type { Environment } from '../api/management-api';
import { listEnvironmentPermissions } from '../api/permissions';
import { LoadPermissionScope } from './permissions';
import { useSession } from './session';

const EnvironmentContext = createContext<Environment | null>(null);

export function useCurrentEnvironment(): Environment {
    const environment = use(EnvironmentContext);
    if (!environment) {
        throw new Error('useCurrentEnvironment() called outside <EnvironmentScope>');
    }
    return environment;
}

export function EnvironmentScope() {
    const { envHrid } = useParams();
    const { user, environments } = useSession();
    const environment = environments.find(env => envHrid !== undefined && env.hrids.includes(envHrid));
    if (!environment) {
        return <Navigate to="/" replace />;
    }
    return (
        <EnvironmentContext value={environment}>
            <LoadPermissionScope
                queryKey={['environment-permissions', user.org, environment.id]}
                queryFn={() => listEnvironmentPermissions(user.org, environment.id)}
            >
                <Outlet />
            </LoadPermissionScope>
        </EnvironmentContext>
    );
}
