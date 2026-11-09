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
import type { PasswordEvaluation, PasswordPolicy } from '../../lib/api/password-policies';

export type RuleStatus = 'valid' | 'invalid' | 'checking';

export type RuleResult = { readonly id: string; readonly description: string; readonly status: RuleStatus };

export type PasswordProfile = { readonly id?: string; readonly firstName?: string; readonly lastName?: string; readonly email?: string };

type LocalRule = {
    readonly id: string;
    readonly description: string;
    readonly passes: (password: string, profile: PasswordProfile) => boolean;
};

const COMMON_PASSWORD = 'Should not be a common password';
const REUSED_PASSWORD = 'Should not reuse a recent password';

function matchRule(id: string, pattern: RegExp, description: string, shouldMatch = true): LocalRule {
    return { id, description, passes: password => pattern.test(password) === shouldMatch };
}

export function localRules(policy: PasswordPolicy): LocalRule[] {
    if (policy.regex) {
        // The default fallback policy holds a regex and no other rule.
        return [matchRule('regex', new RegExp(policy.regex), 'Matches the regular expression configured by admin')];
    }
    const rules: LocalRule[] = [];
    const { minLength, maxConsecutiveLetters } = policy;
    if (minLength) {
        rules.push({
            id: 'minLength',
            description: `Has at least ${minLength} characters`,
            passes: password => password.length >= minLength,
        });
    }
    if (policy.includeNumbers) {
        rules.push(matchRule('numbers', /\d/, 'Contains a number'));
    }
    if (policy.includeSpecialCharacters) {
        rules.push(matchRule('specialCharacters', /[^a-zA-Z0-9]/, 'Contains a special character'));
    }
    if (policy.lettersInMixedCase) {
        rules.push({
            id: 'mixedCase',
            description: 'Contains both lowercase and uppercase letters',
            passes: password => password !== password.toLowerCase() && password !== password.toUpperCase(),
        });
    }
    if (maxConsecutiveLetters) {
        rules.push(
            matchRule(
                'consecutiveChars',
                new RegExp(`(.)\\1{${maxConsecutiveLetters}}`),
                `Doesn't contain the same character more than ${maxConsecutiveLetters} times in a row`,
                false,
            ),
        );
    }
    if (policy.excludeUserProfileInfoInPassword) {
        rules.push({
            id: 'profile',
            description: 'Should not contain information from profile',
            passes: (password, { firstName, lastName, email }) => {
                const lower = password.toLowerCase();
                return (
                    !(firstName && lower.includes(firstName.toLowerCase())) &&
                    !(lastName && lower.includes(lastName.toLowerCase())) &&
                    !(email && lower.includes(email.toLowerCase()))
                );
            },
        });
    }
    return rules;
}

/** The policy checks only the server runs: the dictionary and the password history. */
export function needsServerCheck(policy: PasswordPolicy): boolean {
    return !policy.regex && Boolean(policy.excludePasswordsInDictionary || policy.passwordHistoryEnabled);
}

/** The history check needs a user id. */
export function serverResults(
    policy: PasswordPolicy,
    userId: string | undefined,
    evaluation: PasswordEvaluation | undefined,
): RuleResult[] {
    if (!evaluation) {
        return [
            ...(policy.excludePasswordsInDictionary
                ? [{ id: 'dictionary', description: COMMON_PASSWORD, status: 'checking' as const }]
                : []),
            ...(policy.passwordHistoryEnabled && userId
                ? [{ id: 'history', description: REUSED_PASSWORD, status: 'checking' as const }]
                : []),
        ];
    }
    const results: RuleResult[] = [];
    if (evaluation.excludePasswordsInDictionary !== undefined && evaluation.excludePasswordsInDictionary !== null) {
        results.push({
            id: 'dictionary',
            description: COMMON_PASSWORD,
            status: evaluation.excludePasswordsInDictionary ? 'valid' : 'invalid',
        });
    }
    if (evaluation.recentPasswordsNotReused !== undefined && evaluation.recentPasswordsNotReused !== null && userId) {
        results.push({ id: 'history', description: REUSED_PASSWORD, status: evaluation.recentPasswordsNotReused ? 'valid' : 'invalid' });
    }
    return results;
}
