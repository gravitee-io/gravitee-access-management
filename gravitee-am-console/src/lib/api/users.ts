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
import type { Audit as AuditModel } from '@am-models/Audit';
import type { NewUser as NewUserModel } from '@am-models/NewUser';
import { http } from './http';
import type { Page } from './management-api';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';

// The generated `UserEntity` model does not compile: it imports the `Role` model, which references an undeclared
// `InnerEnum`. These are the fields the console reads. Dates are epoch milliseconds.
export interface User {
    readonly id: string;
    readonly username: string;
    readonly displayName?: string;
    readonly firstName?: string;
    readonly lastName?: string;
    readonly email?: string;
    readonly picture?: string;
    readonly enabled: boolean;
    readonly internal?: boolean;
    readonly serviceAccount?: boolean;
    readonly referenceType?: 'domain' | 'organization';
    readonly source?: string;
    readonly sourceId?: string;
    readonly client?: string | null;
    readonly applicationEntity?: { readonly id?: string; readonly clientId?: string; readonly name?: string };
    readonly additionalInformation?: Record<string, unknown>;
    readonly accountNonLocked?: boolean;
    readonly accountLockedAt?: number | null;
    readonly accountLockedUntil?: number | null;
    readonly preRegistration?: boolean;
    readonly registrationCompleted?: boolean;
    readonly forceResetPassword?: boolean;
    readonly loginsCount?: number;
    readonly loggedAt?: number;
    readonly createdAt?: number;
    readonly lastPasswordReset?: number;
    readonly lastIdentityUsed?: string;
}

export type NewUser = ApiModel<
    Pick<
        NewUserModel,
        'firstName' | 'lastName' | 'email' | 'username' | 'password' | 'client' | 'source' | 'forceResetPassword' | 'preRegistration'
    > & { additionalInformation?: Record<string, unknown> }
>;

/** The eight fields the Angular `UserService.update` sends. `client: null` removes the application. */
export interface UserProfileUpdate {
    readonly firstName?: string;
    readonly lastName?: string;
    readonly displayName?: string;
    readonly email?: string;
    readonly enabled: boolean;
    readonly client?: string | null;
    readonly additionalInformation?: Record<string, unknown>;
    readonly forceResetPassword?: boolean;
}

// Response enums are lowercase on the wire; the generated model types them uppercase.
export type Audit = ApiModel<
    Omit<AuditModel, 'outcome'> & { outcome?: { readonly status?: 'success' | 'failure'; readonly message?: string } },
    'id' | 'type' | 'timestamp'
>;

export interface AuditQuery {
    readonly page: number;
    readonly size: number;
    readonly from: number;
    readonly to: number;
    readonly type?: string;
    readonly status?: 'SUCCESS' | 'FAILURE';
}

/** Plain text or a SCIM filter, as the user typed it, or the filter built from the advanced search. */
export type UserSearch = { readonly text: string } | { readonly filter: string };

const SCIM_OPERATOR = /"(?:\\.|[^"\\])*"|\b[\w.-]+\s+(?<op>pr|eq|ne|co|sw|ew|gt|ge|lt|le)\b/gi;

/** The Angular `isAdvancedSearch`: a search that holds a SCIM operator outside a quoted string. */
export function isScimFilter(search: string): boolean {
    SCIM_OPERATOR.lastIndex = 0;
    let match;
    while ((match = SCIM_OPERATOR.exec(search))) {
        if (match.groups?.op) {
            return true;
        }
    }
    return false;
}

function usersPath(ref: DomainRef): string {
    return `${domainPath(ref)}/users`;
}

function userPath(ref: DomainRef, userId: string): string {
    return `${usersPath(ref)}/${encodeURIComponent(userId)}`;
}

function searchQuery(search: UserSearch): string {
    if ('filter' in search) {
        return search.filter ? `filter=${encodeURIComponent(search.filter)}&` : '';
    }
    if (!search.text) {
        return '';
    }
    // The Angular `UsersComponent` sends plain text as a prefix match on `q`, and a SCIM filter on `filter`.
    return isScimFilter(search.text) ? `filter=${encodeURIComponent(search.text)}&` : `q=${encodeURIComponent(`${search.text}*`)}&`;
}

export function listUsers(ref: DomainRef, search: UserSearch, page: number, size: number): Promise<Page<User>> {
    return http<Page<User>>(`${usersPath(ref)}?${searchQuery(search)}page=${page}&size=${size}`);
}

export function getUser(ref: DomainRef, userId: string): Promise<User> {
    return http<User>(userPath(ref, userId));
}

export function createUser(ref: DomainRef, user: NewUser): Promise<User> {
    return http<User>(usersPath(ref), { method: 'POST', body: JSON.stringify(user) });
}

export function updateUser(ref: DomainRef, userId: string, update: UserProfileUpdate): Promise<User> {
    return http<User>(userPath(ref, userId), { method: 'PUT', body: JSON.stringify(update) });
}

export function updateUsername(ref: DomainRef, userId: string, username: string): Promise<User> {
    return http<User>(`${userPath(ref, userId)}/username`, { method: 'PATCH', body: JSON.stringify({ username }) });
}

export function updateUserStatus(ref: DomainRef, userId: string, enabled: boolean): Promise<User> {
    return http<User>(`${userPath(ref, userId)}/status`, { method: 'PUT', body: JSON.stringify({ enabled }) });
}

export function resetUserPassword(ref: DomainRef, userId: string, password: string): Promise<void> {
    return http<void>(`${userPath(ref, userId)}/resetPassword`, { method: 'POST', body: JSON.stringify({ password }) });
}

export function unlockUser(ref: DomainRef, userId: string): Promise<void> {
    return http<void>(`${userPath(ref, userId)}/unlock`, { method: 'POST', body: '{}' });
}

export function sendRegistrationConfirmation(ref: DomainRef, userId: string): Promise<void> {
    return http<void>(`${userPath(ref, userId)}/sendRegistrationConfirmation`, { method: 'POST', body: '{}' });
}

export function deleteUser(ref: DomainRef, userId: string): Promise<void> {
    return http<void>(userPath(ref, userId), { method: 'DELETE' });
}

export function listUserAudits(ref: DomainRef, userId: string, query: AuditQuery): Promise<Page<Audit>> {
    const params = new URLSearchParams({
        page: String(query.page),
        size: String(query.size),
        from: String(query.from),
        to: String(query.to),
    });
    if (query.type) {
        params.set('type', query.type);
    }
    if (query.status) {
        params.set('status', query.status);
    }
    return http<Page<Audit>>(`${userPath(ref, userId)}/audits?${params}`);
}

export async function isEmailRequired(): Promise<boolean> {
    return (await http<{ emailRequired: boolean }>('/platform/configuration/users/email-required')).emailRequired;
}

export function listAuditEventTypes(): Promise<string[]> {
    return http<string[]>('/platform/audits/events');
}
