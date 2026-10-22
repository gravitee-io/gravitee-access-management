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
import { hash } from 'bcryptjs';
import { http } from './http';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';
import type { IdentityProviderPlugin, Plugin } from './plugins';

export type IdentityProviderSummary = ApiModel<FilteredIdentityProviderInfo, 'id' | 'name' | 'type' | 'system' | 'external'>;

/** `configuration` is the plugin configuration, serialized as a JSON string. System providers do not return it. */
export type IdentityProvider = ApiModel<IdentityProviderModel, 'id' | 'name' | 'type' | 'system' | 'external'>;

export function listIdentityProviders(ref: DomainRef): Promise<IdentityProviderSummary[]> {
    return http<IdentityProviderSummary[]>(`${domainPath(ref)}/identities`);
}

/** The Angular `ProviderService.findUserProvidersByDomain`: the providers that manage users. */
export function listUserIdentityProviders(ref: DomainRef): Promise<IdentityProviderSummary[]> {
    return http<IdentityProviderSummary[]>(`${domainPath(ref)}/identities?userProvider=true`);
}

/** The Angular `ProviderService.assignPasswordPolicy`. Without a policy id, the provider uses the default policy. */
export function assignPasswordPolicy(ref: DomainRef, identityProviderId: string, passwordPolicy: string | undefined): Promise<unknown> {
    return http<unknown>(`${domainPath(ref)}/identities/${identityProviderId}/password-policy`, {
        method: 'PUT',
        body: JSON.stringify({ passwordPolicy }),
    });
}

export function getIdentityProvider(ref: DomainRef, identityProviderId: string): Promise<IdentityProvider> {
    return http<IdentityProvider>(`${domainPath(ref)}/identities/${identityProviderId}`);
}

export function updateIdentityProvider(ref: DomainRef, provider: IdentityProvider): Promise<IdentityProvider> {
    const { name, type, domainWhitelist, mappers, roleMapper, groupMapper } = provider;
    // A system provider comes back without its configuration. Angular sends an empty one, which the API requires.
    const configuration = provider.configuration ?? '{}';
    return http<IdentityProvider>(`${domainPath(ref)}/identities/${provider.id}`, {
        method: 'PUT',
        body: JSON.stringify({ name, type, configuration, domainWhitelist, mappers, roleMapper, groupMapper }),
    });
}

export function deleteIdentityProvider(ref: DomainRef, identityProviderId: string): Promise<void> {
    return http<void>(`${domainPath(ref)}/identities/${identityProviderId}`, { method: 'DELETE' });
}

const INLINE_TYPE = 'inline-am-idp';
const MASKED_PASSWORD = '********';
// bcryptjs 3 writes $2b$, older tools $2a$ or $2y$.
const BCRYPT_HASH = /^\$2[aby]\$/;

interface InlineUser {
    readonly password: string;
}

/**
 * Serializes a plugin configuration for a create or an update. An inline provider with the BCrypt encoder gets its new
 * passwords hashed first, as the Angular `ProviderService.enhanceConfiguration` does: the plugin stores them as sent.
 */
export async function serializeConfiguration(type: string, configuration: Record<string, unknown>): Promise<string> {
    if (type !== INLINE_TYPE || configuration.passwordEncoder !== 'BCrypt' || !Array.isArray(configuration.users)) {
        return JSON.stringify(configuration);
    }
    const users = await Promise.all(
        (configuration.users as InlineUser[]).map(async user =>
            BCRYPT_HASH.test(user.password) || user.password === MASKED_PASSWORD
                ? user
                : { ...user, password: await hash(user.password, 10) },
        ),
    );
    return JSON.stringify({ ...configuration, users });
}

export function getIdentityProviderSchema(type: string): Promise<Record<string, unknown>> {
    return http<Record<string, unknown>>(`/platform/plugins/identities/${type}/schema`);
}

export type NewIdentityProvider = ApiModel<NewIdentityProviderModel, 'external' | 'domainWhitelist'>;

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
