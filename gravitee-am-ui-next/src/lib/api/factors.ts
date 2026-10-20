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
import { http } from './http';
import { type DomainRef, domainPath } from './paths';
import type { Plugin } from './plugins';

/** A factor of the list. The list returns these three fields only. */
export interface FactorSummary {
    readonly id: string;
    readonly name: string;
    readonly factorType?: string;
}

export interface Factor {
    readonly id: string;
    readonly name: string;
    readonly type: string;
    readonly factorType?: string;
    /** Plugin configuration, serialized as a JSON string. */
    readonly configuration: string;
}

export interface NewFactor {
    readonly name: string;
    readonly type: string;
    readonly factorType: string;
    /** Plugin configuration, serialized as a JSON string. */
    readonly configuration: string;
}

export interface FactorPlugin extends Plugin {
    /** The resource category the factor sends its codes through, or `any`. */
    readonly category?: string;
}

export function listFactors(ref: DomainRef): Promise<FactorSummary[]> {
    return http<FactorSummary[]>(`${domainPath(ref)}/factors`);
}

export function getFactor(ref: DomainRef, factorId: string): Promise<Factor> {
    return http<Factor>(`${domainPath(ref)}/factors/${factorId}`);
}

export function createFactor(ref: DomainRef, factor: NewFactor): Promise<Factor> {
    return http<Factor>(`${domainPath(ref)}/factors`, { method: 'POST', body: JSON.stringify(factor) });
}

export function updateFactor(ref: DomainRef, factorId: string, factor: Pick<Factor, 'name' | 'type' | 'configuration'>): Promise<Factor> {
    const { name, type, configuration } = factor;
    return http<Factor>(`${domainPath(ref)}/factors/${factorId}`, { method: 'PUT', body: JSON.stringify({ name, type, configuration }) });
}

export function deleteFactor(ref: DomainRef, factorId: string): Promise<void> {
    return http<void>(`${domainPath(ref)}/factors/${factorId}`, { method: 'DELETE' });
}

export function listFactorPlugins(): Promise<FactorPlugin[]> {
    return http<FactorPlugin[]>('/platform/plugins/factors');
}

export function getFactorSchema(type: string): Promise<Record<string, unknown>> {
    return http<Record<string, unknown>>(`/platform/plugins/factors/${type}/schema`);
}
