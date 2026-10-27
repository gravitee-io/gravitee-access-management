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
import { PageHeader } from './PageHeader';

interface PlaceholderPageProps {
    readonly title: string;
}

export function PlaceholderPage({ title }: PlaceholderPageProps) {
    return (
        <div className="flex flex-col gap-6">
            <PageHeader title={title} />
            <Empty className="border border-border">
                <EmptyHeader>
                    <EmptyMedia variant="icon">
                        <WrenchIcon />
                    </EmptyMedia>
                    <EmptyTitle>Not available yet</EmptyTitle>
                    <EmptyDescription>This page has not moved to the new console yet.</EmptyDescription>
                </EmptyHeader>
            </Empty>
        </div>
    );
}
