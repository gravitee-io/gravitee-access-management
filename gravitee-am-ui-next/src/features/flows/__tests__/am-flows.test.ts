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
import { describe, expect, it } from 'vitest';
import type { AmFlow } from '../../../lib/api/management-api';
import { AM_FLOW_MODEL, toAmFlows, toStudioFlow } from '../am-flows';

const LOGIN: AmFlow = {
    id: 'f-1',
    type: 'login',
    name: 'LOGIN',
    enabled: true,
    condition: '{#request.params.x}',
    pre: [{ policy: 'policy-am-enrich-auth-flow', name: 'Enrich', enabled: true, configuration: '{"idempotent":true}' }],
    post: [{ policy: 'groovy', enabled: false }],
};

describe('AM flows in the policy studio', () => {
    it('maps pre and post steps to request and response, and parses the configuration', () => {
        const flow = toStudioFlow(LOGIN);

        expect(flow.id).toBe('login~f-1');
        expect(flow.selectors).toEqual([{ type: 'CONDITION', condition: '{#request.params.x}' }]);
        expect(flow.request?.[0]).toMatchObject({
            policy: 'policy-am-enrich-auth-flow',
            name: 'Enrich',
            configuration: { idempotent: true },
        });
        expect(flow.response?.[0]).toMatchObject({ policy: 'groovy', enabled: false, configuration: {} });
    });

    it('groups a flow by its type, and a default flow keeps no AM id', () => {
        const defaultRoot = toStudioFlow({ type: 'root', name: 'ALL', enabled: true, pre: [], post: [] });

        expect(AM_FLOW_MODEL.groupOf?.(toStudioFlow(LOGIN))).toBe('login');
        expect(AM_FLOW_MODEL.groupOf?.(defaultRoot)).toBe('root');
        expect(toAmFlows({ flowGroups: [{ id: 'root', flows: [defaultRoot] }] })[0].id).toBeUndefined();
    });

    it('turns the saved groups back into AM flows, typed by group, without HTTP selectors', () => {
        const edited = {
            ...toStudioFlow(LOGIN),
            selectors: [
                { type: 'HTTP' as const, path: '/', pathOperator: 'STARTS_WITH' as const },
                { type: 'CONDITION' as const, condition: '{#true}' },
            ],
        };
        const added = { name: 'Extra login', enabled: true, request: [{ policy: 'groovy', configuration: { script: '' } }] };

        expect(toAmFlows({ flowGroups: [{ id: 'login', flows: [edited, added] }] })).toEqual([
            {
                id: 'f-1',
                type: 'login',
                name: 'LOGIN',
                enabled: true,
                condition: '{#true}',
                pre: [
                    {
                        policy: 'policy-am-enrich-auth-flow',
                        name: 'Enrich',
                        enabled: true,
                        configuration: '{"idempotent":true}',
                        description: undefined,
                        condition: undefined,
                    },
                ],
                post: [
                    {
                        policy: 'groovy',
                        enabled: false,
                        configuration: '{}',
                        name: undefined,
                        description: undefined,
                        condition: undefined,
                    },
                ],
            },
            {
                id: undefined,
                type: 'login',
                name: 'Extra login',
                enabled: true,
                condition: undefined,
                pre: [
                    {
                        policy: 'groovy',
                        enabled: true,
                        configuration: '{"script":""}',
                        name: undefined,
                        description: undefined,
                        condition: undefined,
                    },
                ],
                post: [],
            },
        ]);
    });
});
