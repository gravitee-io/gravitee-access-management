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
import type { PasswordPolicy as PasswordPolicyModel } from '@am-models/PasswordPolicy';
import { HttpError, http } from './http';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';

export type PasswordPolicy = ApiModel<PasswordPolicyModel>;

/** The checks the server ran, as `PasswordPolicyService.evaluatePassword` returns them. */
export interface PasswordEvaluation {
    readonly excludePasswordsInDictionary?: boolean;
    readonly recentPasswordsNotReused?: boolean;
}

/** The policy that applies to a user of the identity provider, or of the domain without one. `null` when none applies. */
export async function getActivePasswordPolicy(ref: DomainRef, identityProviderId?: string): Promise<PasswordPolicy | null> {
    const query = identityProviderId ? `?identity=${encodeURIComponent(identityProviderId)}` : '';
    try {
        return (await http<PasswordPolicy | undefined>(`${domainPath(ref)}/password-policies/activePolicy${query}`)) ?? null;
    } catch (error) {
        // The Angular service skips its 404 redirect here: no policy is not an error.
        if (error instanceof HttpError && error.status === 404) {
            return null;
        }
        throw error;
    }
}

export function evaluatePassword(
    ref: DomainRef,
    policyId: string,
    userId: string | undefined,
    password: string,
): Promise<PasswordEvaluation> {
    return http<PasswordEvaluation>(`${domainPath(ref)}/password-policies/${encodeURIComponent(policyId)}/evaluate`, {
        method: 'POST',
        body: JSON.stringify({ userId, password }),
    });
}
