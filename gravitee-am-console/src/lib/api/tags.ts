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
import type { NewTag } from '@am-models/NewTag';
import type { Tag as TagModel } from '@am-models/Tag';
import { http } from './http';
import type { ApiModel } from './model';

/** A sharding tag of the organization. */
export type Tag = ApiModel<TagModel, 'id' | 'name'>;

export function listTags(organizationId: string): Promise<Tag[]> {
    return http<Tag[]>(`/organizations/${organizationId}/tags`);
}

export function getTag(organizationId: string, tagId: string): Promise<Tag> {
    return http<Tag>(`/organizations/${organizationId}/tags/${encodeURIComponent(tagId)}`);
}

export function createTag(organizationId: string, tag: NewTag): Promise<Tag> {
    return http<Tag>(`/organizations/${organizationId}/tags`, { method: 'POST', body: JSON.stringify(tag) });
}

export function updateTag(organizationId: string, tagId: string, tag: NewTag): Promise<Tag> {
    return http<Tag>(`/organizations/${organizationId}/tags/${encodeURIComponent(tagId)}`, {
        method: 'PUT',
        body: JSON.stringify({ name: tag.name, description: tag.description }),
    });
}

export function deleteTag(organizationId: string, tagId: string): Promise<void> {
    return http<void>(`/organizations/${organizationId}/tags/${encodeURIComponent(tagId)}`, { method: 'DELETE' });
}
