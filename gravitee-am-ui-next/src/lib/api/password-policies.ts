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
import type { NewPasswordPolicy as NewPasswordPolicyModel } from '@am-models/NewPasswordPolicy';
import type { PasswordPolicy as PasswordPolicyModel } from '@am-models/PasswordPolicy';
import type { PasswordPolicyEntity } from '@am-models/PasswordPolicyEntity';
import { http } from './http';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';

/** A row of the list, with the names of the identity providers that use the policy. */
export type PasswordPolicySummary = ApiModel<PasswordPolicyEntity, 'id' | 'name'>;

export type PasswordPolicy = ApiModel<PasswordPolicyModel, 'id' | 'name'>;

export type NewPasswordPolicy = ApiModel<NewPasswordPolicyModel>;

function policiesPath(ref: DomainRef): string {
    return `${domainPath(ref)}/password-policies`;
}

export function listPasswordPolicies(ref: DomainRef): Promise<PasswordPolicySummary[]> {
    return http<PasswordPolicySummary[]>(policiesPath(ref));
}

export function getPasswordPolicy(ref: DomainRef, policyId: string): Promise<PasswordPolicy> {
    return http<PasswordPolicy>(`${policiesPath(ref)}/${policyId}`);
}

export function createPasswordPolicy(ref: DomainRef, policy: NewPasswordPolicy): Promise<PasswordPolicy> {
    return http<PasswordPolicy>(policiesPath(ref), { method: 'POST', body: JSON.stringify(policy) });
}

export function updatePasswordPolicy(ref: DomainRef, policyId: string, policy: NewPasswordPolicy): Promise<PasswordPolicy> {
    return http<PasswordPolicy>(`${policiesPath(ref)}/${policyId}`, { method: 'PUT', body: JSON.stringify(policy) });
}

export function deletePasswordPolicy(ref: DomainRef, policyId: string): Promise<void> {
    return http<void>(`${policiesPath(ref)}/${policyId}`, { method: 'DELETE' });
}

export function setDefaultPasswordPolicy(ref: DomainRef, policyId: string): Promise<unknown> {
    return http<unknown>(`${policiesPath(ref)}/${policyId}/default`, { method: 'POST' });
}
