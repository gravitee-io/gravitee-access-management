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
import type { FilteredIdentityProviderInfo } from '@am-models/FilteredIdentityProviderInfo';
import type { IdentityProvider as IdentityProviderModel } from '@am-models/IdentityProvider';
import type { NewIdentityProvider as NewIdentityProviderModel } from '@am-models/NewIdentityProvider';
import { isRecord } from '../guards';
import { http } from './http';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';

export type IdentityProviderSummary = ApiModel<FilteredIdentityProviderInfo, 'id' | 'name' | 'type' | 'system' | 'external'>;

/** `configuration` is the plugin configuration as a JSON string. A system provider has none. */
export type IdentityProvider = ApiModel<IdentityProviderModel, 'id' | 'name' | 'type' | 'system' | 'external'>;

export type NewIdentityProvider = ApiModel<NewIdentityProviderModel, 'external' | 'domainWhitelist'>;

/** A plugin of `GET /platform/plugins/identities`, with the expanded fields. */
export interface IdentityProviderPlugin {
    readonly id: string;
    readonly name: string;
    readonly displayName?: string;
    /** A data URI. */
    readonly icon?: string;
    readonly labels?: readonly string[];
    /** Absent or blank for an OSS plugin. */
    readonly feature?: string;
    /** Set by the console: true for a plugin of the `external=true` list. */
    readonly external: boolean;
}

export function listIdentityProviders(ref: DomainRef): Promise<IdentityProviderSummary[]> {
    return http<IdentityProviderSummary[]>(`${domainPath(ref)}/identities`);
}

export function listUserIdentityProviders(ref: DomainRef): Promise<IdentityProviderSummary[]> {
    return http<IdentityProviderSummary[]>(`${domainPath(ref)}/identities?userProvider=true`);
}

export function getIdentityProvider(ref: DomainRef, identityProviderId: string): Promise<IdentityProvider> {
    return http<IdentityProvider>(`${domainPath(ref)}/identities/${identityProviderId}`);
}

export function createIdentityProvider(ref: DomainRef, provider: NewIdentityProvider): Promise<IdentityProvider> {
    return http<IdentityProvider>(`${domainPath(ref)}/identities`, { method: 'POST', body: JSON.stringify(provider) });
}

export function updateIdentityProvider(ref: DomainRef, provider: IdentityProvider): Promise<IdentityProvider> {
    const { name, type, domainWhitelist, mappers, roleMapper, groupMapper } = provider;
    // The API requires a configuration. Angular sends an empty one for a system provider.
    const configuration = provider.configuration ?? '{}';
    return http<IdentityProvider>(`${domainPath(ref)}/identities/${provider.id}`, {
        method: 'PUT',
        body: JSON.stringify({ name, type, configuration, domainWhitelist, mappers, roleMapper, groupMapper }),
    });
}

export function deleteIdentityProvider(ref: DomainRef, identityProviderId: string): Promise<void> {
    return http<void>(`${domainPath(ref)}/identities/${identityProviderId}`, { method: 'DELETE' });
}

const EXPAND = 'expand=icon&expand=displayName&expand=labels';

type PluginInfo = Omit<IdentityProviderPlugin, 'external'>;

/** The built-in plugins, then the social and enterprise plugins, as the Angular `IdentitiesResolver` loads them. */
export async function listIdentityProviderPlugins(): Promise<IdentityProviderPlugin[]> {
    const [builtIn, external] = await Promise.all([
        http<PluginInfo[]>(`/platform/plugins/identities?${EXPAND}`),
        http<PluginInfo[]>(`/platform/plugins/identities?external=true&${EXPAND}`),
    ]);
    return [...builtIn.map(plugin => ({ ...plugin, external: false })), ...external.map(plugin => ({ ...plugin, external: true }))];
}

/** The API answers 204 when it has no schema for the plugin. The form then has no configuration fields. */
export async function getIdentityProviderSchema(type: string): Promise<Record<string, unknown>> {
    return (await http<Record<string, unknown> | undefined>(`/platform/plugins/identities/${type}/schema`)) ?? {};
}

const INLINE_TYPE = 'inline-am-idp';
const MASKED_PASSWORD = '********';
/** The stored configuration as an object. */
export function parseConfiguration(provider: IdentityProvider): Record<string, unknown> {
    const parsed: unknown = JSON.parse(provider.configuration ?? '{}');
    return isRecord(parsed) ? parsed : {};
}

// bcryptjs 3 writes $2b$. Older tools write $2a$ or $2y$.
const BCRYPT_HASH = /^\$2[aby]\$/;

/**
 * The configuration as a JSON string. An inline provider with the BCrypt encoder gets its new passwords hashed first,
 * as the Angular `ProviderService.enhanceConfiguration` does: the plugin stores them as sent.
 */
export async function serializeConfiguration(type: string, configuration: Record<string, unknown>): Promise<string> {
    if (type !== INLINE_TYPE || configuration.passwordEncoder !== 'BCrypt' || !Array.isArray(configuration.users)) {
        return JSON.stringify(configuration);
    }
    const { hash } = await import('bcryptjs');
    const users = await Promise.all(
        configuration.users.map(async (user: unknown) =>
            !isRecord(user) || typeof user.password !== 'string' || BCRYPT_HASH.test(user.password) || user.password === MASKED_PASSWORD
                ? user
                : { ...user, password: await hash(user.password, 10) },
        ),
    );
    return JSON.stringify({ ...configuration, users });
}
