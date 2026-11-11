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
import { isNotFound } from '../../lib/api/http';
import { getTag } from '../../lib/api/tags';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useSession } from '../../lib/session/session';
import { TagDetail } from './TagDetail';
import { useOrganizationPath } from './useOrganizationPath';

export function TagPage() {
    const { tagId = '' } = useParams();
    const { user } = useSession();
    const base = useOrganizationPath();
    const listPath = `${base}/tags`;
    const { data, error, isPending, refetch } = useQuery({
        queryKey: ['tag', user.org, tagId],
        queryFn: () => getTag(user.org, tagId),
        retry: false,
    });
    useBreadcrumbs([
        { label: 'Organization', to: `${base}/general` },
        { label: 'Sharding tags', to: listPath },
        { label: data?.name ?? tagId },
    ]);

    if (error) {
        return isNotFound(error) ? (
            <NotFoundPanel
                title="Sharding tag not found"
                description="This sharding tag does not exist. It may have been deleted."
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to sharding tags</Link>
                    </Button>
                }
            />
        ) : (
            <ErrorPanel description="Could not load the sharding tag." error={error} onRetry={() => void refetch()} />
        );
    }
    if (isPending) {
        return <Skeleton className="h-72 w-full" />;
    }
    return <TagDetail key={data.id} tag={data} listPath={listPath} />;
}
