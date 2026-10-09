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
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@gravitee/graphene-core';
import { WrenchIcon } from '@gravitee/graphene-core/icons';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { PageHeader } from './PageHeader';

/** A page the Gamma prototype has and the spike has not built. */
export function PlaceholderPage({ title }: { readonly title: string }) {
    return (
        <div className="flex flex-col gap-6">
            <PageHeader title={title} />
            <Empty className="border">
                <EmptyHeader>
                    <EmptyMedia variant="icon">
                        <WrenchIcon />
                    </EmptyMedia>
                    <EmptyTitle>Not built yet</EmptyTitle>
                    <EmptyDescription>The Gamma prototype has this page. The spike does not.</EmptyDescription>
                </EmptyHeader>
            </Empty>
        </div>
    );
}

/** A placeholder for a top-level domain section, which sets its own breadcrumbs. */
export function DomainPlaceholderPage({ title }: { readonly title: string }) {
    const { domain, basePath, domainsPath } = useCurrentDomain();
    useBreadcrumbs([{ label: 'Domains', to: domainsPath }, { label: domain.name, to: `${basePath}/dashboard` }, { label: title }]);
    return <PlaceholderPage title={title} />;
}
