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
import type { ConsoleUserPreferences } from '@am-models/ConsoleUserPreferences';
import type { Environment as EnvironmentModel } from '@am-models/Environment';
import { http } from './http';
import type { ApiModel } from './model';

/** Claims of the console JWT plus the flattened platform and organization permissions, as `GET /user` returns them. */
export interface CurrentUser {
    readonly sub: string;
    readonly org: string;
    readonly preferred_username?: string;
    readonly name?: string;
    readonly given_name?: string;
    readonly family_name?: string;
    readonly email?: string;
    readonly permissions: string[];
}

export type Environment = ApiModel<EnvironmentModel, 'id' | 'name' | 'hrids'>;

// The console sends null to unset the default domain.
export type UserPreferences = ApiModel<
    Omit<ConsoleUserPreferences, 'defaultDomainId' | 'defaultEnvironmentId'> & {
        defaultDomainId?: string | null;
        defaultEnvironmentId?: string | null;
    }
>;

export interface Page<T> {
    readonly data: T[];
    readonly currentPage: number;
    readonly totalCount: number;
}

export function getCurrentUser(): Promise<CurrentUser> {
    return http<CurrentUser>('/user');
}

export function getUserPreferences(): Promise<UserPreferences> {
    return http<UserPreferences>('/user/preferences');
}

export function updateUserPreferences(preferences: UserPreferences): Promise<UserPreferences> {
    return http<UserPreferences>('/user/preferences', { method: 'PUT', body: JSON.stringify(preferences) });
}

export function listEnvironments(organizationId: string): Promise<Environment[]> {
    return http<Environment[]>(`/organizations/${organizationId}/environments`);
}
