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
import type { FilteredIdentityProviderInfo } from '@am-models/FilteredIdentityProviderInfo';
import { http } from './http';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';

export type IdentityProviderSummary = ApiModel<FilteredIdentityProviderInfo, 'id' | 'name' | 'type' | 'system' | 'external'>;

/** The providers that manage users. */
export function listUserIdentityProviders(ref: DomainRef): Promise<IdentityProviderSummary[]> {
    return http<IdentityProviderSummary[]>(`${domainPath(ref)}/identities?userProvider=true`);
}
