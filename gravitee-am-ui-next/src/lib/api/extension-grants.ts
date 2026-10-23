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
import type { ExtensionGrant as ExtensionGrantModel } from '@am-models/ExtensionGrant';
import type { NewExtensionGrant as NewExtensionGrantModel } from '@am-models/NewExtensionGrant';
import type { UpdateExtensionGrant as UpdateExtensionGrantModel } from '@am-models/UpdateExtensionGrant';
import { http } from './http';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';
import type { Plugin } from './plugins';

/** An extension grant of the list. The list returns these four fields only. */
export type ExtensionGrantSummary = Pick<ExtensionGrant, 'id' | 'name' | 'type' | 'grantType'>;

/** `configuration` is the plugin configuration, serialized as a JSON string. */
export type ExtensionGrant = ApiModel<ExtensionGrantModel, 'id' | 'name' | 'type' | 'configuration'>;

export type NewExtensionGrant = ApiModel<NewExtensionGrantModel>;

export type UpdateExtensionGrant = ApiModel<UpdateExtensionGrantModel>;

export function listExtensionGrants(ref: DomainRef): Promise<ExtensionGrantSummary[]> {
    return http<ExtensionGrantSummary[]>(`${domainPath(ref)}/extensionGrants`);
}

export function getExtensionGrant(ref: DomainRef, extensionGrantId: string): Promise<ExtensionGrant> {
    return http<ExtensionGrant>(`${domainPath(ref)}/extensionGrants/${extensionGrantId}`);
}

export function createExtensionGrant(ref: DomainRef, extensionGrant: NewExtensionGrant): Promise<ExtensionGrant> {
    return http<ExtensionGrant>(`${domainPath(ref)}/extensionGrants`, { method: 'POST', body: JSON.stringify(extensionGrant) });
}

export function updateExtensionGrant(
    ref: DomainRef,
    extensionGrantId: string,
    extensionGrant: UpdateExtensionGrant,
): Promise<ExtensionGrant> {
    const { name, type, configuration, grantType, identityProvider, createUser, userExists } = extensionGrant;
    return http<ExtensionGrant>(`${domainPath(ref)}/extensionGrants/${extensionGrantId}`, {
        method: 'PUT',
        body: JSON.stringify({ name, type, configuration, grantType, identityProvider, createUser, userExists }),
    });
}

export function deleteExtensionGrant(ref: DomainRef, extensionGrantId: string): Promise<void> {
    return http<void>(`${domainPath(ref)}/extensionGrants/${extensionGrantId}`, { method: 'DELETE' });
}

export function listExtensionGrantPlugins(): Promise<Plugin[]> {
    return http<Plugin[]>('/platform/plugins/extensionGrants');
}

/** A plugin that is not deployed answers 204 with no body. */
export async function getExtensionGrantSchema(type: string): Promise<Record<string, unknown>> {
    return (await http<Record<string, unknown> | undefined>(`/platform/plugins/extensionGrants/${type}/schema`)) ?? {};
}
