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
export interface DomainRef {
    readonly organizationId: string;
    readonly environmentId: string;
    readonly domainId: string;
}

export function environmentPath(organizationId: string, environmentId: string): string {
    return `/organizations/${organizationId}/environments/${environmentId}`;
}

export function domainPath(ref: DomainRef): string {
    return `${environmentPath(ref.organizationId, ref.environmentId)}/domains/${ref.domainId}`;
}

// The Management API matches `q` as a wildcard pattern, so a free-text search becomes `*text*`.
export function searchParam(query: string): string {
    return query ? `&q=${encodeURIComponent(`*${query}*`)}` : '';
}
