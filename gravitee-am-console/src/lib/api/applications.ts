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
import type { FilteredApplication } from '@am-models/FilteredApplication';
import { http } from './http';
import type { Page } from './management-api';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';

export type ApplicationSummary = ApiModel<Pick<FilteredApplication, 'id' | 'name' | 'clientId'>, 'id' | 'name'>;

export function listApplications(ref: DomainRef, page: number, size: number): Promise<Page<ApplicationSummary>> {
    return http<Page<ApplicationSummary>>(`${domainPath(ref)}/applications?page=${page}&size=${size}`);
}

/** The Angular `ApplicationService.search`: a prefix match on the name or the client id. */
export function searchApplications(ref: DomainRef, text: string): Promise<Page<ApplicationSummary>> {
    return http<Page<ApplicationSummary>>(`${domainPath(ref)}/applications?q=${encodeURIComponent(`${text}*`)}`);
}
