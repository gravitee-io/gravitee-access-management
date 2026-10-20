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
import type { IdentityProviderPlugin, Plugin } from './plugins';

export interface IdentityProviderSummary {
    readonly id: string;
    readonly name: string;
    readonly type: string;
    readonly system: boolean;
    readonly external: boolean;
}

export interface IdentityProvider extends IdentityProviderSummary {
    /** Plugin configuration, serialized as a JSON string. System providers do not return it. */
    readonly configuration?: string;
    readonly domainWhitelist?: string[];
    readonly mappers?: Record<string, unknown>;
    readonly roleMapper?: Record<string, unknown>;
    readonly groupMapper?: Record<string, unknown>;
}

export function listIdentityProviders(ref: DomainRef): Promise<IdentityProviderSummary[]> {
    return http<IdentityProviderSummary[]>(`${domainPath(ref)}/identities`);
}

export function getIdentityProvider(ref: DomainRef, identityProviderId: string): Promise<IdentityProvider> {
    return http<IdentityProvider>(`${domainPath(ref)}/identities/${identityProviderId}`);
}

export function updateIdentityProvider(ref: DomainRef, provider: IdentityProvider): Promise<IdentityProvider> {
    const { name, type, configuration, domainWhitelist, mappers, roleMapper, groupMapper } = provider;
    return http<IdentityProvider>(`${domainPath(ref)}/identities/${provider.id}`, {
        method: 'PUT',
        body: JSON.stringify({ name, type, configuration, domainWhitelist, mappers, roleMapper, groupMapper }),
    });
}

export function getIdentityProviderSchema(type: string): Promise<Record<string, unknown>> {
    return http<Record<string, unknown>>(`/platform/plugins/identities/${type}/schema`);
}

export interface NewIdentityProvider {
    readonly name: string;
    readonly type: string;
    /** Plugin configuration, serialized as a JSON string. */
    readonly configuration: string;
    readonly external: boolean;
    readonly domainWhitelist: readonly string[];
}

export function createIdentityProvider(ref: DomainRef, provider: NewIdentityProvider): Promise<IdentityProvider> {
    return http<IdentityProvider>(`${domainPath(ref)}/identities`, { method: 'POST', body: JSON.stringify(provider) });
}

const EXPAND = 'expand=icon&expand=displayName&expand=labels';

/** The built-in plugins, then the social and enterprise plugins, as the Angular `IdentitiesResolver` loads them. */
export async function listIdentityProviderPlugins(): Promise<IdentityProviderPlugin[]> {
    const [builtIn, external] = await Promise.all([
        http<Plugin[]>(`/platform/plugins/identities?${EXPAND}`),
        http<Plugin[]>(`/platform/plugins/identities?external=true&${EXPAND}`),
    ]);
    return [...builtIn.map(plugin => ({ ...plugin, external: false })), ...external.map(plugin => ({ ...plugin, external: true }))];
}
