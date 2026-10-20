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
import { Button, Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@gravitee/graphene-core';
import { LockIcon } from '@gravitee/graphene-core/icons';
import { upgradeUrl } from '../../lib/session/license';

interface UpgradePanelProps {
    readonly feature: string;
    readonly description: string;
}

/** The state of a route the Angular `LicenseGuard` blocks, because the licence lacks its feature. */
export function UpgradePanel({ feature, description }: UpgradePanelProps) {
    return (
        <Empty className="border border-border">
            <EmptyHeader>
                <EmptyMedia variant="icon">
                    <LockIcon />
                </EmptyMedia>
                <EmptyTitle>Enterprise feature</EmptyTitle>
                <EmptyDescription>{description}</EmptyDescription>
            </EmptyHeader>
            <EmptyContent>
                <Button asChild>
                    <a href={upgradeUrl(feature)} target="_blank" rel="noreferrer">
                        Request upgrade
                    </a>
                </Button>
            </EmptyContent>
        </Empty>
    );
}
