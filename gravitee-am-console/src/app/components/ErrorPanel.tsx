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
import { TriangleAlertIcon } from '@gravitee/graphene-core/icons';

interface ErrorPanelProps {
    /** What did not load, as a sentence. */
    readonly description: string;
    readonly error?: unknown;
    /** Shows a Retry button that reloads what failed. */
    readonly onRetry?: () => void;
}

export function ErrorPanel({ description, error, onRetry }: ErrorPanelProps) {
    const message = error instanceof Error ? error.message : undefined;
    return (
        <Empty role="alert" className="border border-border">
            <EmptyHeader>
                <EmptyMedia variant="icon" className="bg-destructive/10 text-destructive">
                    <TriangleAlertIcon />
                </EmptyMedia>
                <EmptyTitle>Unable to load</EmptyTitle>
                <EmptyDescription>{description}</EmptyDescription>
            </EmptyHeader>
            {(message || onRetry) && (
                <EmptyContent>
                    {message && <p className="font-mono text-xs text-muted-foreground">{message}</p>}
                    {onRetry && (
                        <Button type="button" variant="outline" size="sm" onClick={onRetry}>
                            Retry
                        </Button>
                    )}
                </EmptyContent>
            )}
        </Empty>
    );
}
