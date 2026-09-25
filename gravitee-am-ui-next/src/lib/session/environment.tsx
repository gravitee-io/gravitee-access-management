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
import { useSession } from './session';

const EnvironmentContext = createContext<Environment | null>(null);

export function useCurrentEnvironment(): Environment {
    const environment = use(EnvironmentContext);
    if (!environment) {
        throw new Error('useCurrentEnvironment() called outside <EnvironmentScope>');
    }
    return environment;
}

/** Resolves `:envHrid` against the user's environments. `/auth/cockpit` lands on `/environments/<hrid>`. */
export function EnvironmentScope() {
    const { envHrid } = useParams();
    const { environments } = useSession();
    const environment = environments.find(env => envHrid !== undefined && env.hrids.includes(envHrid));

    if (!environment) {
        return <Navigate to="/" replace />;
    }
    return (
        <EnvironmentContext value={environment}>
            <Outlet />
        </EnvironmentContext>
    );
}

export function RootRedirect() {
    const { environments } = useSession();
    const first = environments[0];

    if (!first) {
        return <p className="p-6 text-sm text-muted-foreground">No environment is available to your account.</p>;
    }
    return <Navigate to={`/environments/${first.hrids[0]}`} replace />;
}
