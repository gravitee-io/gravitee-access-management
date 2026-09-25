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
import { createContext, type ReactNode, use } from 'react';
import { type CurrentUser, type Environment, getCurrentUser, listEnvironments } from '../api/management-api';

export interface Session {
    readonly user: CurrentUser;
    readonly environments: Environment[];
}

const SessionContext = createContext<Session | null>(null);

export function useSession(): Session {
    const session = use(SessionContext);
    if (!session) {
        throw new Error('useSession() called outside <SessionGate>');
    }
    return session;
}

async function loadSession(): Promise<Session> {
    const user = await getCurrentUser();
    const environments = await listEnvironments(user.org);
    return { user, environments };
}

/**
 * Renders its children once `GET /user` succeeds. A 401 is handled by the query cache, which starts the login chain,
 * so the gate keeps showing the spinner while the browser leaves.
 */
export function SessionGate({ children }: { readonly children: ReactNode }) {
    const { data, error } = useQuery({ queryKey: ['session'], queryFn: loadSession, staleTime: Infinity });

    if (data) {
        return <SessionContext value={data}>{children}</SessionContext>;
    }
    if (error && !('status' in error && error.status === 401)) {
        return <p className="p-6 text-sm text-destructive">Could not load the current user.</p>;
    }
    return (
        <div className="flex h-screen items-center justify-center">
            <Spinner />
        </div>
    );
}
