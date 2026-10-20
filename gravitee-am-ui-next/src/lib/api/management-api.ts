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
import type { Environment as EnvironmentModel } from '@am-models/Environment';
import type { InstallationConfiguration as InstallationConfigurationModel } from '@am-models/InstallationConfiguration';
import { http, isNotFound } from './http';
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

export interface Page<T> {
    readonly data: T[];
    readonly currentPage: number;
    readonly totalCount: number;
}

export function getCurrentUser(): Promise<CurrentUser> {
    return http<CurrentUser>('/user');
}

export function listEnvironments(organizationId: string): Promise<Environment[]> {
    return http<Environment[]>(`/organizations/${organizationId}/environments`);
}

export type InstallationConfiguration = ApiModel<InstallationConfigurationModel>;

/** The installation configuration. An installation without the endpoint reads as an empty configuration. */
export function getInstallationConfiguration(): Promise<InstallationConfiguration> {
    return http<InstallationConfiguration>('/platform/configuration/installation').catch((error: unknown) => {
        if (isNotFound(error)) {
            return {};
        }
        throw error;
    });
}
