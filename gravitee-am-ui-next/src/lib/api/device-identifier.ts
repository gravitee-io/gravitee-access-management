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
import type { DeviceIdentifier as DeviceIdentifierModel } from '@am-models/DeviceIdentifier';
import type { NewDeviceIdentifier as NewDeviceIdentifierModel } from '@am-models/NewDeviceIdentifier';
import type { UpdateDeviceIdentifier as UpdateDeviceIdentifierModel } from '@am-models/UpdateDeviceIdentifier';
import { http } from './http';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';
import type { Plugin } from './plugins';

/** A device identifier of the list. The list returns these three fields only. */
export type DeviceIdentifierSummary = Pick<DeviceIdentifier, 'id' | 'name' | 'type'>;

/** `configuration` is the plugin configuration, serialized as a JSON string. */
export type DeviceIdentifier = ApiModel<DeviceIdentifierModel, 'id' | 'name' | 'type' | 'configuration'>;

export type NewDeviceIdentifier = ApiModel<NewDeviceIdentifierModel>;

export type UpdateDeviceIdentifier = ApiModel<UpdateDeviceIdentifierModel>;

export function listDeviceIdentifiers(ref: DomainRef): Promise<DeviceIdentifierSummary[]> {
    return http<DeviceIdentifierSummary[]>(`${domainPath(ref)}/device-identifiers`);
}

export function getDeviceIdentifier(ref: DomainRef, deviceIdentifierId: string): Promise<DeviceIdentifier> {
    return http<DeviceIdentifier>(`${domainPath(ref)}/device-identifiers/${deviceIdentifierId}`);
}

export function createDeviceIdentifier(ref: DomainRef, deviceIdentifier: NewDeviceIdentifier): Promise<DeviceIdentifier> {
    return http<DeviceIdentifier>(`${domainPath(ref)}/device-identifiers`, { method: 'POST', body: JSON.stringify(deviceIdentifier) });
}

export function updateDeviceIdentifier(
    ref: DomainRef,
    deviceIdentifierId: string,
    deviceIdentifier: UpdateDeviceIdentifier,
): Promise<DeviceIdentifier> {
    const { name, type, configuration } = deviceIdentifier;
    return http<DeviceIdentifier>(`${domainPath(ref)}/device-identifiers/${deviceIdentifierId}`, {
        method: 'PUT',
        body: JSON.stringify({ name, type, configuration }),
    });
}

export function deleteDeviceIdentifier(ref: DomainRef, deviceIdentifierId: string): Promise<void> {
    return http<void>(`${domainPath(ref)}/device-identifiers/${deviceIdentifierId}`, { method: 'DELETE' });
}

export function listDeviceIdentifierPlugins(): Promise<Plugin[]> {
    return http<Plugin[]>('/platform/plugins/device-identifiers');
}

// A plugin that is not installed has no schema: the endpoint answers 204, and the form then has no configuration fields.
export async function getDeviceIdentifierSchema(type: string): Promise<Record<string, unknown>> {
    return (await http<Record<string, unknown> | undefined>(`/platform/plugins/device-identifiers/${type}/schema`)) ?? {};
}
