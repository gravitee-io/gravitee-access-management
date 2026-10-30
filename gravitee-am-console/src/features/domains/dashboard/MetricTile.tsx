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
import { Metric } from '@gravitee/graphene-charts';
import { Card, CardContent } from '@gravitee/graphene-core';
import type { ComponentType } from 'react';
import { count, type WidgetResult } from './dashboard-data';

interface MetricTileProps {
    readonly label: string;
    readonly icon: ComponentType<{ className?: string }>;
    readonly result: WidgetResult;
    /** Written before a positive value. */
    readonly prefix?: string;
}

export function MetricTile({ label, icon, result, prefix = '' }: MetricTileProps) {
    const value = result.status === 'ready' ? count(result.data) : undefined;
    return (
        <Card>
            <CardContent>
                {result.status === 'loading' ? (
                    <Metric label={label} icon={icon} value="" state="loading" />
                ) : value === undefined ? (
                    <>
                        <Metric label={label} icon={icon} value="" state="empty" />
                        {result.status === 'error' && <p className="text-sm text-muted-foreground">Failed to fetch data</p>}
                    </>
                ) : (
                    <Metric label={label} icon={icon} value={`${value > 0 ? prefix : ''}${value.toLocaleString()}`} />
                )}
            </CardContent>
        </Card>
    );
}
