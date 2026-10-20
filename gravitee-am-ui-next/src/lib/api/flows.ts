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
import type { Step } from '@am-models/Step';
import { http, httpText } from './http';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';

/** `configuration` is the policy configuration, serialized as a JSON string. */
export type FlowStep = ApiModel<Step, 'policy' | 'enabled'>;

export interface AmFlow {
    /** Absent while the domain still uses the default flow for this type. */
    readonly id?: string | null;
    readonly type: string;
    readonly name: string;
    readonly enabled: boolean;
    readonly condition?: string | null;
    readonly pre: FlowStep[];
    readonly post: FlowStep[];
    readonly icon?: string;
}

export interface PolicyPlugin {
    readonly id: string;
    readonly name: string;
    readonly description?: string;
    readonly version?: string;
    readonly deployed?: boolean;
    /** Licence feature that unlocks the policy. */
    readonly feature?: string;
    /** Data URI, returned with `expand=icon`. */
    readonly icon?: string;
}

function flowsPath(ref: DomainRef, applicationId?: string): string {
    const domain = domainPath(ref);
    return applicationId ? `${domain}/applications/${applicationId}/flows` : `${domain}/flows`;
}

/** The flows of the domain, or of one application when `applicationId` is set. */
export function listFlows(ref: DomainRef, applicationId?: string): Promise<AmFlow[]> {
    return http<AmFlow[]>(flowsPath(ref, applicationId));
}

/** Replaces every flow of the domain or application. The order within a type is the execution order. */
export function updateFlows(ref: DomainRef, flows: AmFlow[], applicationId?: string): Promise<AmFlow[]> {
    return http<AmFlow[]>(flowsPath(ref, applicationId), {
        method: 'PUT',
        body: JSON.stringify(flows),
    });
}

export function listPolicies(): Promise<PolicyPlugin[]> {
    return http<PolicyPlugin[]>('/platform/plugins/policies?expand=icon');
}

export function getPolicySchema(policyId: string): Promise<Record<string, unknown>> {
    return http<Record<string, unknown>>(`/platform/plugins/policies/${policyId}/schema`);
}

/** AsciiDoc. */
export function getPolicyDocumentation(policyId: string): Promise<string> {
    return httpText(`/platform/plugins/policies/${policyId}/documentation`);
}
