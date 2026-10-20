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
import { createContext, type ReactNode, use, useMemo } from 'react';
import { Outlet } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { ForbiddenPanel } from '../../app/components/ForbiddenPanel';

/**
 * The permission names in effect, as AM grants them: the user's platform and organization permissions from
 * `GET /user`, plus one list per scope from `/members/permissions`. Each scope adds its list to its parent's.
 */
const PermissionContext = createContext<readonly string[]>([]);

export interface Permissions {
    /** True when the user holds every name. The Angular `hasPermission` directive and `hasPermissions()`. */
    readonly has: (...permissions: string[]) => boolean;
    /** True when the user holds at least one name. The Angular route `perms.only` and `hasAnyPermissions()`. */
    readonly hasAny: (...permissions: string[]) => boolean;
}

export function usePermissions(): Permissions {
    const granted = use(PermissionContext);
    return useMemo(
        () => ({
            has: (...permissions) => permissions.every(permission => granted.includes(permission)),
            hasAny: (...permissions) => permissions.some(permission => granted.includes(permission)),
        }),
        [granted],
    );
}

interface PermissionScopeProps {
    /** The names this scope grants, added to the ones already in effect. */
    readonly permissions: readonly string[];
    readonly children: ReactNode;
}

export function PermissionScope({ permissions, children }: PermissionScopeProps) {
    const inherited = use(PermissionContext);
    const granted = useMemo(() => [...inherited, ...permissions], [inherited, permissions]);
    return <PermissionContext value={granted}>{children}</PermissionContext>;
}

interface LoadPermissionScopeProps {
    readonly queryKey: readonly unknown[];
    readonly queryFn: () => Promise<string[]>;
    readonly children: ReactNode;
}

/** Loads a scope's list before it renders the children, as the Angular `AuthGuard` does before a route activates. */
export function LoadPermissionScope({ queryKey, queryFn, children }: LoadPermissionScopeProps) {
    const { data, error } = useQuery({ queryKey, queryFn, staleTime: Infinity });

    if (error) {
        return <ErrorPanel description="Could not load your permissions." error={error} />;
    }
    if (!data) {
        return <Spinner />;
    }
    return <PermissionScope permissions={data}>{children}</PermissionScope>;
}

interface RequirePermissionProps {
    /** The route's `perms.only` list from Angular. One match is enough. */
    readonly anyOf: readonly string[];
    /** The page. Omit on a pathless layout route: the child routes render through `Outlet`. */
    readonly children?: ReactNode;
}

/** A route element that replaces the Angular `AuthGuard`. */
export function RequirePermission({ anyOf, children }: RequirePermissionProps) {
    const { hasAny } = usePermissions();
    if (!hasAny(...anyOf)) {
        return <ForbiddenPanel />;
    }
    return children ?? <Outlet />;
}

interface CanProps {
    /** Every name is required. The Angular `*hasPermission`. */
    readonly all?: readonly string[];
    /** One name is enough. The Angular `*hasAnyPermission`. */
    readonly any?: readonly string[];
    readonly children: ReactNode;
    readonly fallback?: ReactNode;
}

/** Renders the children only for a user who holds the permissions. A gate for a button or a column. */
export function Can({ all = [], any = [], children, fallback = null }: CanProps) {
    const { has, hasAny } = usePermissions();
    const allowed = has(...all) && (any.length === 0 || hasAny(...any));
    return <>{allowed ? children : fallback}</>;
}
