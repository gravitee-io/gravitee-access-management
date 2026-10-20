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
import { http } from './http';
import type { Page } from './management-api';
import { type DomainRef, domainPath } from './paths';

export interface Role {
    readonly id: string;
    readonly name: string;
    readonly system?: boolean;
}

const ROLES_PAGE_SIZE = 50;

export function listRoles(ref: DomainRef, page: number, size: number): Promise<Page<Role>> {
    return http<Page<Role>>(`${domainPath(ref)}/roles?page=${page}&size=${size}`);
}

/** Every role of the domain, page by page, as the Angular `RoleService.findAllByDomain` loads them. */
export async function listAllRoles(ref: DomainRef): Promise<Role[]> {
    const first = await listRoles(ref, 0, ROLES_PAGE_SIZE);
    const pageCount = Math.ceil(first.totalCount / ROLES_PAGE_SIZE);
    const rest = await Promise.all(Array.from({ length: pageCount - 1 }, (_, index) => listRoles(ref, index + 1, ROLES_PAGE_SIZE)));
    return [first, ...rest].flatMap(page => page.data);
}
