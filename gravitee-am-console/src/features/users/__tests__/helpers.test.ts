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
import { isScimFilter, type User } from '../../../lib/api/users';
import { auditDetails, rangeEnd, rangeStart } from '../audit-helpers';
import { localRules, needsServerCheck, serverResults } from '../password-rules';
import { additionalInformationOf, advancedSearchFilter, attributesOf, displayNameOf, isAccountLocked, userStatus } from '../user-helpers';

const user = (patch: Partial<User>): User => ({ id: 'u', username: 'u', enabled: true, accountNonLocked: true, ...patch });

describe('isScimFilter', () => {
    it.each([
        ['userName eq "alice"', true],
        ['emails.value co "acme" and active pr', true],
        ['alice', false],
        ['"name eq alice"', false],
    ])('reads %s as a SCIM filter: %s', (search, expected) => {
        expect(isScimFilter(search)).toBe(expected);
    });
});

describe('advancedSearchFilter', () => {
    it('joins the values of a criterion with or and the criteria with and', () => {
        expect(advancedSearchFilter({ applications: ['a1', 'a2'], providers: ['p1'], disabledOnly: true })).toBe(
            '(client eq "a1" or client eq "a2") and source eq "p1" and enabled eq false',
        );
    });

    it('groups each criterion with several values, as the SCIM parser binds and before or', () => {
        expect(advancedSearchFilter({ applications: ['a1'], providers: ['p1', 'p2'], disabledOnly: false })).toBe(
            'client eq "a1" and (source eq "p1" or source eq "p2")',
        );
    });

    it('is empty with no criterion', () => {
        expect(advancedSearchFilter({ applications: [], providers: [], disabledOnly: false })).toBe('');
    });
});

describe('user status', () => {
    it('is locked while the lock has no end or ends later', () => {
        expect(isAccountLocked(user({ accountNonLocked: false }), 1000)).toBe(true);
        expect(isAccountLocked(user({ accountNonLocked: false, accountLockedUntil: 2000 }), 1000)).toBe(true);
        expect(isAccountLocked(user({ accountNonLocked: false, accountLockedUntil: 500 }), 1000)).toBe(false);
    });

    it('reads disabled before locked', () => {
        expect(userStatus(user({ enabled: false, accountNonLocked: false }))).toBe('disabled');
        expect(userStatus(user({ accountNonLocked: false }))).toBe('locked');
        expect(userStatus(user({}))).toBe('enabled');
    });
});

describe('additional information', () => {
    const stored = { team: 'blue', address: { city: 'Oxford' }, hidden: null };

    it('shows the non-null values, an object as read-only JSON', () => {
        expect(attributesOf(stored)).toEqual([
            { name: 'team', value: 'blue', stored: true, readOnly: false },
            { name: 'address', value: '{"city":"Oxford"}', stored: true, readOnly: true },
        ]);
    });

    it('keeps the stored object and null values, and adds the named rows with a value', () => {
        const rows = [
            { name: 'team', value: 'red', stored: true, readOnly: false },
            { name: 'address', value: 'ignored', stored: true, readOnly: true },
            { name: 'role', value: 'admin', stored: false, readOnly: false },
            { name: 'empty', value: '', stored: false, readOnly: false },
        ];

        expect(additionalInformationOf(rows, stored)).toEqual({ hidden: null, team: 'red', address: { city: 'Oxford' }, role: 'admin' });
    });

    it('keeps the type of a boolean or number value that was not edited', () => {
        const claims = { email_verified: true, updated_at: 1699000000, logins: 3 };
        const rows = attributesOf(claims).map(row => (row.name === 'logins' ? { ...row, value: '4' } : row));

        expect(additionalInformationOf(rows, claims)).toEqual({ email_verified: true, updated_at: 1699000000, logins: '4' });
    });
});

describe('displayNameOf', () => {
    it('joins the names, else takes the username', () => {
        expect(displayNameOf('Alice', '', 'alice')).toBe('Alice');
        expect(displayNameOf('Alice', 'Liddell', 'alice')).toBe('Alice Liddell');
        expect(displayNameOf('', '', 'alice')).toBe('alice');
    });
});

describe('password rules', () => {
    it('checks only the admin regex of the default policy', () => {
        const rules = localRules({ regex: '^a+$', minLength: 8 });

        expect(rules.map(rule => rule.description)).toEqual(['Matches the regular expression configured by admin']);
        expect(rules[0].passes('aaa', {})).toBe(true);
    });

    it('checks each rule of a policy with its message', () => {
        const rules = localRules({
            minLength: 4,
            includeNumbers: true,
            includeSpecialCharacters: true,
            lettersInMixedCase: true,
            maxConsecutiveLetters: 3,
            excludeUserProfileInfoInPassword: true,
        });
        const results = (password: string) => rules.map(rule => rule.passes(password, { firstName: 'alice' }));

        expect(rules.map(rule => rule.description)).toEqual([
            'Has at least 4 characters',
            'Contains a number',
            'Contains a special character',
            'Contains both lowercase and uppercase letters',
            "Doesn't contain the same character 3 times in a row",
            'Should not contain information from profile',
        ]);
        expect(results('Ab1!')).toEqual([true, true, true, true, true, true]);
        expect(results('aaa')).toEqual([false, false, false, false, false, true]);
        expect(results('Alice1!')).toEqual([true, true, true, true, true, false]);
    });

    it('asks the server for the dictionary and the history, the history only for a known user', () => {
        const policy = { id: 'p', excludePasswordsInDictionary: true, passwordHistoryEnabled: true };

        expect(needsServerCheck(policy)).toBe(true);
        expect(serverResults(policy, undefined, undefined).map(result => result.id)).toEqual(['dictionary']);
        expect(serverResults(policy, 'u-1', { excludePasswordsInDictionary: true, recentPasswordsNotReused: false })).toEqual([
            { id: 'dictionary', description: 'Should not be a common password', status: 'valid' },
            { id: 'history', description: 'Should not reuse a recent password', status: 'invalid' },
        ]);
    });
});

describe('audit helpers', () => {
    it('shows a success message as indented JSON, a failure as is, and the type with no message', () => {
        expect(auditDetails({ id: 'a', type: 'T', timestamp: 0, outcome: { status: 'success', message: '{"a":1}' } })).toBe(
            '{\n  "a": 1\n}',
        );
        expect(auditDetails({ id: 'a', type: 'T', timestamp: 0, outcome: { status: 'failure', message: 'bad' } })).toBe('bad');
        expect(auditDetails({ id: 'a', type: 'T', timestamp: 0, outcome: { status: 'success' } })).toBe('T success');
    });

    it('starts a quick range before now', () => {
        const now = Date.UTC(2026, 10, 9, 12);
        expect(rangeStart('1h', now)).toBe(now - 3_600_000);
        expect(rangeStart('30d', now)).toBe(now - 28 * 86_400_000);
        expect(new Date(rangeStart('90d', now)).getUTCMonth()).toBe(7);
    });

    it('ends a picked range at the last millisecond of its end day', () => {
        const end = rangeEnd(new Date(2026, 10, 9));

        expect(end).toBe(new Date(2026, 10, 10).getTime() - 1);
    });
});
