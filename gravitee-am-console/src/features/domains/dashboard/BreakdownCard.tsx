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
import { BreakdownChart } from '@gravitee/graphene-charts';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@gravitee/graphene-core';
import { WidgetFailure } from './WidgetFailure';
import { type Group, sentenceCase, type WidgetResult } from './dashboard-data';

interface BreakdownCardProps {
    readonly title: string;
    readonly description: string;
    readonly items: Group[];
    readonly result: WidgetResult;
    /** Orders the items by value, largest first, and sizes the bars against the largest. */
    readonly sort?: boolean;
    readonly maxItems?: number;
    readonly onItemClick?: (key: string) => void;
}

export function BreakdownCard({ title, description, items, result, sort, maxItems, onItemClick }: BreakdownCardProps) {
    return (
        <Card>
            <CardHeader>
                <CardTitle>{title}</CardTitle>
                <CardDescription>{description}</CardDescription>
            </CardHeader>
            <CardContent>
                {result.status === 'error' ? (
                    <WidgetFailure />
                ) : result.status === 'ready' && items.every(item => item.value === 0) ? (
                    <p className="py-6 text-center text-sm text-muted-foreground">No activity in this period.</p>
                ) : (
                    <BreakdownChart
                        items={items.map(item => ({ key: item.key, label: sentenceCase(item.label), value: item.value }))}
                        state={result.status === 'loading' ? 'loading' : 'ready'}
                        sortByValue={sort ? 'desc' : undefined}
                        ratioMode={sort ? 'max' : 'total'}
                        maxItems={maxItems}
                        formatValue={value => value.toLocaleString()}
                        onItemClick={onItemClick && (item => onItemClick(item.key))}
                    />
                )}
            </CardContent>
        </Card>
    );
}
