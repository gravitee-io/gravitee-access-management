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
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@gravitee/graphene-core';
import { CircleXIcon } from '@gravitee/graphene-core/icons';
import type { ReactNode } from 'react';

interface NotFoundPanelProps {
    /** Example: "Application not found". */
    readonly title: string;
    /** Example: "No application with this id exists in Acme. It may have been deleted." */
    readonly description: string;
    /** A way back, such as a link to the list. */
    readonly action?: ReactNode;
}

/** The state of a page whose entity the Management API answered with 404. */
export function NotFoundPanel({ title, description, action }: NotFoundPanelProps) {
    return (
        <Empty className="border border-border">
            <EmptyHeader>
                <EmptyMedia variant="icon">
                    <CircleXIcon />
                </EmptyMedia>
                <EmptyTitle>{title}</EmptyTitle>
                <EmptyDescription>{description}</EmptyDescription>
            </EmptyHeader>
            {action && <EmptyContent>{action}</EmptyContent>}
        </Empty>
    );
}
