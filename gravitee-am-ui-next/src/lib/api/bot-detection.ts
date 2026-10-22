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
import type { BotDetection as BotDetectionModel } from '@am-models/BotDetection';
import type { NewBotDetection as NewBotDetectionModel } from '@am-models/NewBotDetection';
import type { UpdateBotDetection as UpdateBotDetectionModel } from '@am-models/UpdateBotDetection';
import { http } from './http';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';
import type { Plugin } from './plugins';

/** A bot detection of the list. The list returns these four fields only. */
export type BotDetectionSummary = Pick<BotDetection, 'id' | 'name' | 'type' | 'detectionType'>;

/** `configuration` is the plugin configuration, serialized as a JSON string. */
export type BotDetection = ApiModel<BotDetectionModel, 'id' | 'name' | 'type' | 'configuration'>;

export type NewBotDetection = ApiModel<NewBotDetectionModel>;

export type UpdateBotDetection = ApiModel<UpdateBotDetectionModel>;

export function listBotDetections(ref: DomainRef): Promise<BotDetectionSummary[]> {
    return http<BotDetectionSummary[]>(`${domainPath(ref)}/bot-detections`);
}

export function getBotDetection(ref: DomainRef, botDetectionId: string): Promise<BotDetection> {
    return http<BotDetection>(`${domainPath(ref)}/bot-detections/${botDetectionId}`);
}

export function createBotDetection(ref: DomainRef, botDetection: NewBotDetection): Promise<BotDetection> {
    return http<BotDetection>(`${domainPath(ref)}/bot-detections`, { method: 'POST', body: JSON.stringify(botDetection) });
}

export function updateBotDetection(ref: DomainRef, botDetectionId: string, botDetection: UpdateBotDetection): Promise<BotDetection> {
    const { name, type, configuration } = botDetection;
    return http<BotDetection>(`${domainPath(ref)}/bot-detections/${botDetectionId}`, {
        method: 'PUT',
        body: JSON.stringify({ name, type, configuration }),
    });
}

export function deleteBotDetection(ref: DomainRef, botDetectionId: string): Promise<void> {
    return http<void>(`${domainPath(ref)}/bot-detections/${botDetectionId}`, { method: 'DELETE' });
}

export function listBotDetectionPlugins(): Promise<Plugin[]> {
    return http<Plugin[]>('/platform/plugins/bot-detections');
}

export function getBotDetectionSchema(type: string): Promise<Record<string, unknown>> {
    return http<Record<string, unknown>>(`/platform/plugins/bot-detections/${type}/schema`);
}
