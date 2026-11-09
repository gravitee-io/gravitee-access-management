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
import { Spinner, useDebouncedValue } from '@gravitee/graphene-core';
import { CircleCheckIcon, CircleXIcon } from '@gravitee/graphene-core/icons';
import { useQuery } from '@tanstack/react-query';
import { evaluatePassword, type PasswordPolicy } from '../../lib/api/password-policies';
import { useCurrentDomain } from '../../lib/session/domain';
import { localRules, needsServerCheck, type PasswordProfile, type RuleResult, serverResults } from './password-rules';

const SERVER_CHECK_DELAY_MS = 250;

export type PasswordCheck = { readonly results: RuleResult[]; readonly valid: boolean };

/**
 * The Angular `PasswordPolicyStatusComponent` as a hook: the policy rules against the password, the server rules once
 * typing settles. `policy` is undefined while it loads, and null when no policy applies.
 */
export function usePasswordCheck(policy: PasswordPolicy | null | undefined, password: string, profile: PasswordProfile): PasswordCheck {
    const { ref } = useCurrentDomain();
    const settled = useDebouncedValue(password, SERVER_CHECK_DELAY_MS);
    const serverCheck = Boolean(policy?.id) && needsServerCheck(policy ?? {});
    const evaluation = useQuery({
        queryKey: ['password-evaluation', ref, policy?.id, profile.id, settled],
        queryFn: () => evaluatePassword(ref, policy?.id ?? '', profile.id, settled),
        enabled: serverCheck && settled !== '',
        // The key holds the typed password: drop it from the cache as soon as nothing reads it.
        gcTime: 0,
    });

    if (policy === undefined || password === '') {
        return { results: [], valid: false };
    }
    if (policy === null) {
        return { results: [], valid: true };
    }
    const local = localRules(policy).map(
        (rule): RuleResult => ({
            id: rule.id,
            description: rule.description,
            status: rule.passes(password, profile) ? 'valid' : 'invalid',
        }),
    );
    const current = settled === password && evaluation.isSuccess ? evaluation.data : undefined;
    const results = serverCheck ? [...local, ...serverResults(policy, profile.id, current)] : local;
    return { results, valid: results.every(result => result.status === 'valid') };
}

export function PasswordRuleList({ results }: { readonly results: readonly RuleResult[] }) {
    if (results.length === 0) {
        return null;
    }
    return (
        <ul aria-label="Password policy" className="flex flex-col gap-1 text-sm">
            {results.map(result => (
                <li key={result.id} className="flex items-center gap-2">
                    {result.status === 'valid' && <CircleCheckIcon role="img" aria-label="Passed" className="size-4 text-success" />}
                    {result.status === 'invalid' && <CircleXIcon role="img" aria-label="Failed" className="size-4 text-destructive" />}
                    {result.status === 'checking' && <Spinner aria-label="Checking" className="size-4" />}
                    <span>{result.description}</span>
                </li>
            ))}
        </ul>
    );
}
