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
import { Button, toast, useLayoutConfig } from '@gravitee/graphene-core';
import { TriangleAlertIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Outlet } from 'react-router-dom';
import { enableDomain } from '../../lib/api/domains';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';

/** The Angular `DomainComponent`: a banner that enables a disabled domain. */
export function DomainLayout() {
    const { domain, ref } = useCurrentDomain();
    const { has } = usePermissions();
    const queryClient = useQueryClient();
    const enable = useMutation({
        mutationFn: () => enableDomain(ref),
        onSuccess: updated => {
            void queryClient.invalidateQueries({ queryKey: ['domain', ref.organizationId, ref.environmentId, domain.hrid] });
            void queryClient.invalidateQueries({ queryKey: ['domains'] });
            toast.success(`Domain ${updated.name} enabled`);
        },
        onError: error => toast.error(`Could not enable the domain. ${error.message}`),
    });

    const showBanner = !domain.enabled && has('domain_settings_update');
    useLayoutConfig(
        {
            banner: showBanner ? (
                <div
                    role="status"
                    aria-label="Domain status"
                    className="flex items-center gap-2 border-b border-warning/30 bg-warning/15 px-5 py-1.5"
                >
                    <TriangleAlertIcon className="size-4 shrink-0 text-warning" aria-hidden />
                    <span className="text-sm text-foreground">
                        <strong className="text-foreground">{domain.name}</strong> domain is disabled.
                    </span>
                    <div className="flex-1" />
                    <Button variant="outline" size="sm" disabled={enable.isPending} onClick={() => enable.mutate()}>
                        {enable.isPending ? 'Enabling…' : 'Enable domain'}
                    </Button>
                </div>
            ) : null,
        },
        [showBanner, domain.name, enable.isPending],
    );

    return <Outlet />;
}
