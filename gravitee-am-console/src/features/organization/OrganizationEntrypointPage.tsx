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
import { Button, Skeleton } from '@gravitee/graphene-core';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { NotFoundPanel } from '../../app/components/NotFoundPanel';
import { getOrganizationEntrypoint } from '../../lib/api/entrypoints';
import { isNotFound } from '../../lib/api/http';
import { listTags } from '../../lib/api/tags';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { usePermissions } from '../../lib/session/permissions';
import { useSession } from '../../lib/session/session';
import { OrganizationEntrypointDetail } from './OrganizationEntrypointDetail';
import { useOrganizationPath } from './useOrganizationPath';

export function OrganizationEntrypointPage() {
    const { entrypointId = '' } = useParams();
    const { user } = useSession();
    const base = useOrganizationPath();
    const listPath = `${base}/entrypoints`;
    const entrypoint = useQuery({
        queryKey: ['organization-entrypoint', user.org, entrypointId],
        queryFn: () => getOrganizationEntrypoint(user.org, entrypointId),
        retry: false,
    });
    const { has } = usePermissions();
    const canListTags = has('organization_tag_list');
    const tags = useQuery({ queryKey: ['tags', user.org], queryFn: () => listTags(user.org), enabled: canListTags });
    useBreadcrumbs([
        { label: 'Organization', to: `${base}/general` },
        { label: 'Entrypoints', to: listPath },
        { label: entrypoint.data?.name ?? entrypointId },
    ]);

    const error = entrypoint.error ?? tags.error;
    if (error) {
        return isNotFound(error) ? (
            <NotFoundPanel
                title="Entrypoint not found"
                description="This entrypoint does not exist. It may have been deleted."
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to entrypoints</Link>
                    </Button>
                }
            />
        ) : (
            <ErrorPanel
                description="Could not load the entrypoint."
                error={error}
                onRetry={() => void Promise.all([entrypoint.refetch(), tags.refetch()])}
            />
        );
    }
    if (!entrypoint.data || (canListTags && !tags.data)) {
        return <Skeleton className="h-72 w-full" />;
    }
    return (
        <OrganizationEntrypointDetail
            key={entrypoint.data.id}
            entrypoint={entrypoint.data}
            tags={canListTags ? tags.data : undefined}
            listPath={listPath}
        />
    );
}
