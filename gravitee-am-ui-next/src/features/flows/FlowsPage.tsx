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
import { Spinner, toast } from '@gravitee/graphene-core';
import { type Policy, PolicyStudio, type SaveOutput } from '@gravitee/graphene-policy-studio';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { type ReactNode, useCallback, useMemo } from 'react';
import { PageHeader } from '../../app/components/PageHeader';
import { type AmFlow, getPolicyDocumentation, getPolicySchema, listFlows, listPolicies, updateFlows } from '../../lib/api/management-api';
import { useCurrentDomain } from '../../lib/session/domain';
import { adaptAmSchema } from '../identity-providers/am-schema';
import { AM_FLOW_MODEL, toAmFlows, toStudioFlow, toStudioPolicy } from './am-flows';

const NONE = [] as const;
const FLOW_EXECUTION = { mode: 'DEFAULT' } as const;

interface FlowsPageProps {
    /** Edits this application's flows instead of the domain's. */
    readonly applicationId?: string;
    /** Shows only these flow types. Flows of the other types are kept on save. */
    readonly types?: readonly string[];
    readonly description?: string;
    readonly actions?: ReactNode;
}

export function FlowsPage({
    applicationId,
    types,
    description = 'Policies that run before and after each step of the authentication journey.',
    actions,
}: FlowsPageProps) {
    const { ref } = useCurrentDomain();
    const flowsKey = useMemo(() => ['flows', ref, applicationId ?? 'domain'], [ref, applicationId]);
    const { data: flows, error: flowsError } = useQuery({ queryKey: flowsKey, queryFn: () => listFlows(ref, applicationId) });
    const { data: plugins, error: pluginsError } = useQuery({ queryKey: ['policies'], queryFn: listPolicies, staleTime: Infinity });

    const shown = useCallback((flow: AmFlow) => !types || types.includes(flow.type), [types]);
    const commonFlows = useMemo(() => flows?.filter(shown).map(toStudioFlow) ?? [], [flows, shown]);
    const flowModel = useMemo(
        () => (types ? { ...AM_FLOW_MODEL, groups: AM_FLOW_MODEL.groups?.filter(group => types.includes(group.id)) } : AM_FLOW_MODEL),
        [types],
    );
    const policies = useMemo(() => plugins?.map(toStudioPolicy) ?? [], [plugins]);
    const fetchSchema = useCallback(async (policy: Policy) => adaptAmSchema(await getPolicySchema(policy.id)).schema, []);
    const fetchDocumentation = useCallback(
        async (policy: Policy) => ({ content: await getPolicyDocumentation(policy.id), language: 'ASCIIDOC' as const }),
        [],
    );
    const queryClient = useQueryClient();
    const onSave = useCallback(
        async (output: SaveOutput) => {
            if (!output.flowGroups) {
                return;
            }
            try {
                const hidden = flows?.filter(flow => !shown(flow)) ?? [];
                queryClient.setQueryData(flowsKey, await updateFlows(ref, [...toAmFlows(output), ...hidden], applicationId));
                toast.success('Flows saved.');
            } catch (error) {
                toast.error(`Could not save the flows. ${(error as Error).message}`);
                throw error;
            }
        },
        [queryClient, ref, applicationId, flows, shown, flowsKey],
    );

    if (flowsError || pluginsError) {
        return <p className="text-sm text-destructive">Could not load the flows.</p>;
    }
    if (!flows || !plugins) {
        return <Spinner />;
    }
    return (
        <div className="flex h-[calc(100vh-9rem)] flex-col gap-4">
            <PageHeader title="Flows" description={description} actions={actions} />
            <div className="min-h-0 flex-1 overflow-hidden rounded-md border">
                <PolicyStudio
                    apiType="PROXY"
                    scope="ORGANIZATION"
                    flowModel={flowModel}
                    policies={policies}
                    sharedPolicyGroups={NONE}
                    plans={NONE}
                    commonFlows={commonFlows}
                    entrypointsInfo={NONE}
                    endpointsInfo={NONE}
                    flowExecution={FLOW_EXECUTION}
                    onSave={onSave}
                    onFetchPolicySchema={fetchSchema}
                    onFetchPolicyDocumentation={fetchDocumentation}
                />
            </div>
        </div>
    );
}
