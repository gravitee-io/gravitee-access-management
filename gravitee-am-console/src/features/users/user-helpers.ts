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
import type { User } from '../../lib/api/users';

export type UserStatus = 'enabled' | 'disabled' | 'locked';

export function isAccountLocked(user: User, now = Date.now()): boolean {
    return !user.accountNonLocked && (!user.accountLockedUntil || user.accountLockedUntil > now);
}

export function userStatus(user: User): UserStatus {
    if (!user.enabled) {
        return 'disabled';
    }
    return isAccountLocked(user) ? 'locked' : 'enabled';
}

export function isPendingRegistration(user: User): boolean {
    return Boolean(user.preRegistration && !user.registrationCompleted);
}

export type AdvancedSearch = { readonly applications: string[]; readonly providers: string[]; readonly disabledOnly: boolean };

export const NO_ADVANCED_SEARCH: AdvancedSearch = { applications: [], providers: [], disabledOnly: false };

// The SCIM parser binds `and` before `or`, so a criterion with several values needs its parentheses.
function anyOf(clauses: string[]): string {
    return clauses.length > 1 ? `(${clauses.join(' or ')})` : clauses.join('');
}

export function advancedSearchFilter({ applications, providers, disabledOnly }: AdvancedSearch): string {
    return [
        anyOf(applications.map(id => `client eq "${id}"`)),
        anyOf(providers.map(id => `source eq "${id}"`)),
        disabledOnly ? 'enabled eq false' : '',
    ]
        .filter(Boolean)
        .join(' and ');
}

/** A stored row keeps its name. */
export type Attribute = { readonly name: string; readonly value: string; readonly stored: boolean; readonly readOnly: boolean };

export function attributesOf(info: Record<string, unknown> | undefined): Attribute[] {
    return Object.entries(info ?? {})
        .filter(([, value]) => value !== null && value !== undefined)
        .map(([name, value]) =>
            typeof value === 'object'
                ? { name, value: JSON.stringify(value), stored: true, readOnly: true }
                : { name, value: String(value), stored: true, readOnly: false },
        );
}

/** A stored null value is kept, though no row shows it. */
export function additionalInformationOf(attributes: readonly Attribute[], stored: Record<string, unknown> = {}): Record<string, unknown> {
    const result: Record<string, unknown> = {};
    for (const [name, value] of Object.entries(stored)) {
        if (value === null || value === undefined) {
            result[name] = value;
        }
    }
    for (const attribute of attributes) {
        if (attribute.readOnly) {
            result[attribute.name] = stored[attribute.name];
        } else if (attribute.stored) {
            // A row holds its value as text: an unedited boolean or number goes back as stored.
            const storedValue = stored[attribute.name];
            result[attribute.name] = attribute.value === String(storedValue) ? storedValue : attribute.value;
        } else if (attribute.name && attribute.value) {
            result[attribute.name] = attribute.value;
        }
    }
    return result;
}

export function displayNameOf(firstName: string, lastName: string, username: string): string {
    return firstName || lastName ? [firstName, lastName].filter(Boolean).join(' ') : username;
}

export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
