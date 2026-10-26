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
import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Navigate } from 'react-router-dom';
import { getDomain } from '../../lib/api/domains';
import type { CurrentUser, Environment, UserPreferences } from '../../lib/api/management-api';
import { useUserPreferences } from '../../lib/session/preferences';
import { useSession } from '../../lib/session/session';

// The default domain opens on the first visit of `/` only, as the Angular `consumeDefaultDomainRedirect()`.
let defaultDomainRedirectPending = true;

interface LandingPaths {
    readonly environment: string;
    /** Null when the user set no default domain, or it no longer exists. */
    readonly defaultDomain: string | null;
}

async function landingPaths(
    user: CurrentUser,
    environments: readonly Environment[],
    preferences: UserPreferences,
): Promise<LandingPaths | null> {
    const environment = environments.find(env => env.id === preferences.defaultEnvironmentId) ?? environments[0];
    if (!environment) {
        return null;
    }
    const environmentPath = `/environments/${environment.hrids[0]}`;
    const domainId = preferences.defaultDomainId;
    const defaultDomain = domainId
        ? await getDomain(user.org, environment.id, domainId).then(
              domain => `${environmentPath}/domains/${domain.hrid}`,
              () => null,
          )
        : null;
    return { environment: environmentPath, defaultDomain };
}

export function HomePage() {
    const { user, environments } = useSession();
    const { data: preferences, isError: preferencesFailed } = useUserPreferences();
    const { data: paths, isPending } = useQuery({
        queryKey: ['landing-paths', user.org],
        queryFn: () => landingPaths(user, environments, preferences ?? {}),
        enabled: !!preferences || preferencesFailed,
        staleTime: Infinity,
    });
    const path = paths && (defaultDomainRedirectPending && paths.defaultDomain ? paths.defaultDomain : paths.environment);

    useEffect(() => {
        if (paths) {
            defaultDomainRedirectPending = false;
        }
    }, [paths]);

    if (isPending) {
        return (
            <div className="flex h-screen items-center justify-center">
                <Spinner />
            </div>
        );
    }
    if (path) {
        return <Navigate to={path} replace />;
    }
    return (
        <div className="mx-auto flex max-w-2xl flex-col gap-4 p-10">
            <h1>You don&apos;t have any environment yet!</h1>
            <p className="text-sm text-muted-foreground">
                Environment allows to define and isolate different parts of your organization such as <code>production</code>,{' '}
                <code>out of production</code>, <code>internal</code> or <code>public</code>. Once initialized, you will be able to create
                security domains there.
            </p>
            <p className="text-sm">
                Ask your administrator to get access to an environment and start using Identity and Access Management Platform.
            </p>
            <p className="text-sm">
                Don&apos;t know how to create such environment? Have a look at{' '}
                <a className="underline" href="https://gravitee.io/products/cockpit" target="_blank" rel="noreferrer">
                    https://gravitee.io/products/cockpit
                </a>
                .
            </p>
        </div>
    );
}
