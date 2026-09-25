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
import { useCallback, useMemo } from 'react';
import { PageHeader } from '../../app/components/PageHeader';
import { getPolicyDocumentation, getPolicySchema, listFlows, listPolicies, updateFlows } from '../../lib/api/management-api';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { adaptAmSchema } from '../identity-providers/am-schema';
import { AM_FLOW_MODEL, toAmFlows, toStudioFlow, toStudioPolicy } from './am-flows';

const NONE = [] as const;
const FLOW_EXECUTION = { mode: 'DEFAULT' } as const;

export function FlowsPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const { data: flows, error: flowsError } = useQuery({ queryKey: ['flows', ref], queryFn: () => listFlows(ref) });
    const { data: plugins, error: pluginsError } = useQuery({ queryKey: ['policies'], queryFn: listPolicies, staleTime: Infinity });
    useBreadcrumbs([{ label: 'Domains', to: domainsPath }, { label: domain.name, to: basePath }, { label: 'Flows' }]);

    const commonFlows = useMemo(() => flows?.map(toStudioFlow) ?? [], [flows]);
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
                queryClient.setQueryData(['flows', ref], await updateFlows(ref, toAmFlows(output)));
                toast.success('Flows saved.');
            } catch (error) {
                toast.error(`Could not save the flows. ${(error as Error).message}`);
                throw error;
            }
        },
        [queryClient, ref],
    );

    if (flowsError || pluginsError) {
        return <p className="text-sm text-destructive">Could not load the flows.</p>;
    }
    if (!flows || !plugins) {
        return <Spinner />;
    }
    return (
        <div className="flex h-[calc(100vh-9rem)] flex-col gap-4">
            <PageHeader title="Flows" description="Policies that run before and after each step of the authentication journey." />
            <div className="min-h-0 flex-1 overflow-hidden rounded-md border">
                <PolicyStudio
                    apiType="PROXY"
                    scope="ORGANIZATION"
                    flowModel={AM_FLOW_MODEL}
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
