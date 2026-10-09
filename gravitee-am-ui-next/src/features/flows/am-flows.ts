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
import type { Flow, FlowModel, Policy, SaveOutput, Step } from '@gravitee/graphene-policy-studio';
import type { AmFlow, FlowStep, PolicyPlugin } from '../../lib/api/management-api';

// The studio runs at organization scope, where a flow has request and response steps. AM's pre steps
// go in `request` and post steps in `response`. The studio id carries the AM flow type, so the
// FlowModel can group flows by type: `<type>~<id>`, or `<type>~default` for a type's unsaved default flow.

const FLOW_TYPES: readonly { readonly type: string; readonly label: string }[] = [
    { type: 'root', label: 'All' },
    { type: 'login_identifier', label: 'Login identifier' },
    { type: 'login', label: 'Login' },
    { type: 'connect', label: 'Connect' },
    { type: 'consent', label: 'Consent' },
    { type: 'register', label: 'Register' },
    { type: 'reset_password', label: 'Reset password' },
    { type: 'registration_confirmation', label: 'Registration confirmation' },
    { type: 'token', label: 'Token' },
    { type: 'webauthn_register', label: 'WebAuthn register' },
    { type: 'mfa_challenge', label: 'MFA challenge' },
    { type: 'mfa_enrollment', label: 'MFA enrollment' },
];

const DEFAULT_ID = 'default';

function studioId(flow: AmFlow): string {
    return `${flow.type}~${flow.id ?? DEFAULT_ID}`;
}

function amId(studioFlowId: string | undefined): string | undefined {
    const id = studioFlowId?.split('~')[1];
    return id === DEFAULT_ID ? undefined : id;
}

export const AM_FLOW_MODEL: FlowModel = {
    phases: {
        REQUEST: {
            label: 'Pre',
            title: 'Pre steps',
            description: 'Policies that run before Access Management handles this step.',
            startActorLabel: 'User',
            endActorLabel: 'Access Management',
        },
        RESPONSE: {
            label: 'Post',
            title: 'Post steps',
            description: 'Policies that run after Access Management has handled this step.',
            startActorLabel: 'Access Management',
            endActorLabel: 'User',
        },
    },
    groups: FLOW_TYPES.map(({ type, label }) => ({ id: type, label })),
    groupOf: flow => flow.id?.split('~')[0] ?? 'root',
    conditionOnly: true,
    showStepNames: true,
};

function toStudioStep(step: FlowStep): Step {
    return {
        name: step.name,
        policy: step.policy,
        description: step.description,
        enabled: step.enabled,
        condition: step.condition,
        configuration: step.configuration ? JSON.parse(step.configuration) : {},
    };
}

function toAmStep(step: Step): FlowStep {
    return {
        name: step.name,
        policy: step.policy ?? '',
        description: step.description,
        enabled: step.enabled ?? true,
        condition: step.condition,
        configuration: JSON.stringify(step.configuration ?? {}),
    };
}

export function toStudioFlow(flow: AmFlow): Flow {
    return {
        id: studioId(flow),
        name: flow.name,
        enabled: flow.enabled,
        selectors: flow.condition ? [{ type: 'CONDITION', condition: flow.condition }] : [],
        request: flow.pre.map(toStudioStep),
        response: flow.post.map(toStudioStep),
    };
}

// The condition-only flow form still emits an HTTP selector; AM flows have none, so it is dropped.
function toAmFlow(flow: Flow, type: string): AmFlow {
    const condition = flow.selectors?.find(selector => selector.type === 'CONDITION')?.condition;
    return {
        id: amId(flow.id),
        type,
        name: flow.name ?? '',
        enabled: flow.enabled ?? true,
        condition: condition || undefined,
        pre: (flow.request ?? []).map(toAmStep),
        post: (flow.response ?? []).map(toAmStep),
    };
}

/** The full flow list for `PUT .../flows`, in group order. */
export function toAmFlows(output: SaveOutput): AmFlow[] {
    return (output.flowGroups ?? []).flatMap(group => group.flows.map(flow => toAmFlow(flow, group.id)));
}

export function toStudioPolicy(plugin: PolicyPlugin): Policy {
    return {
        id: plugin.id,
        name: plugin.name,
        description: plugin.description,
        version: plugin.version,
        deployed: plugin.deployed,
        icon: plugin.icon,
    };
}
