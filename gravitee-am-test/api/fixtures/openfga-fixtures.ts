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

import { Tuple } from '@management-commands/openfga-settings-commands';

/**
 * MCP Server authorization model with owner and viewer relations
 * Allows access if user is either owner or viewer
 */
export const mcpAuthorizationModel = {
  schema_version: '1.1',
  type_definitions: [
    {
      type: 'user',
    },
    {
      type: 'tool',
      relations: {
        owner: {
          this: {},
        },
        viewer: {
          this: {},
        },
        can_access: {
          union: {
            child: [
              {
                computedUserset: {
                  relation: 'owner',
                },
              },
              {
                computedUserset: {
                  relation: 'viewer',
                },
              },
            ],
          },
        },
        can_manage: {
          computedUserset: {
            relation: 'owner',
          },
        },
      },
      metadata: {
        relations: {
          owner: {
            directly_related_user_types: [
              {
                type: 'user',
              },
            ],
          },
          viewer: {
            directly_related_user_types: [
              {
                type: 'user',
              },
            ],
          },
        },
      },
    },
  ],
};

/**
 * Factory functions for creating test tuples
 */
export const tupleFactory = {
  ownerTuple: (userId: string, objectId: string, objectType: string = 'tool'): Tuple => ({
    user: `user:${userId}`,
    relation: 'owner',
    object: `${objectType}:${objectId}`,
  }),

  viewerTuple: (userId: string, objectId: string, objectType: string = 'tool'): Tuple => ({
    user: `user:${userId}`,
    relation: 'viewer',
    object: `${objectType}:${objectId}`,
  }),
};

/**
 * Factory functions for creating permission checks (Management API)
 */
export const checkFactory = {
  canAccess: (userId: string, objectId: string, objectType: string = 'tool') => ({
    user: `user:${userId}`,
    relation: 'can_access',
    object: `${objectType}:${objectId}`,
  }),

  canManage: (userId: string, objectId: string, objectType: string = 'tool') => ({
    user: `user:${userId}`,
    relation: 'can_manage',
    object: `${objectType}:${objectId}`,
  }),
};

/**
 * Factory functions for creating AuthZen evaluation requests (Gateway API)
 */
export const authzenFactory = {
  canAccess: (userId: string, objectId: string, objectType: string = 'tool', properties?: any) => ({
    subject: { type: 'user', id: userId, ...(properties?.subject && { properties: properties.subject }) },
    resource: { type: objectType, id: objectId, ...(properties?.resource && { properties: properties.resource }) },
    action: { name: 'can_access', ...(properties?.action && { properties: properties.action }) },
    ...(properties?.context && { context: properties.context }),
  }),

  canManage: (userId: string, objectId: string, objectType: string = 'tool', properties?: any) => ({
    subject: { type: 'user', id: userId, ...(properties?.subject && { properties: properties.subject }) },
    resource: { type: objectType, id: objectId, ...(properties?.resource && { properties: properties.resource }) },
    action: { name: 'can_manage', ...(properties?.action && { properties: properties.action }) },
    ...(properties?.context && { context: properties.context }),
  }),

  custom: (subjectType: string, subjectId: string, resourceType: string, resourceId: string, actionName: string, properties?: any) => ({
    subject: { type: subjectType, id: subjectId, ...(properties?.subject && { properties: properties.subject }) },
    resource: { type: resourceType, id: resourceId, ...(properties?.resource && { properties: properties.resource }) },
    action: { name: actionName, ...(properties?.action && { properties: properties.action }) },
    ...(properties?.context && { context: properties.context }),
  }),
};
