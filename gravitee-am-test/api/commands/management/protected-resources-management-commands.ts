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

import { getProtectedResourcesApi } from './service/utils';
import { NewProtectedResource } from '@management-models/NewProtectedResource';
import { UpdateProtectedResource } from '@management-models/UpdateProtectedResource';
import { ProtectedResourcePrimaryData, ProtectedResourceSecret } from '@management-models/index';
import { ProtectedResourcePage } from '@management-models/ProtectedResourcePage';

export const createProtectedResource = (
  domainId: string,
  accessToken: string,
  body: NewProtectedResource,
): Promise<ProtectedResourceSecret> =>
  getProtectedResourcesApi(accessToken).createProtectedResource({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    newProtectedResource: body,
  });

export const updateProtectedResource = (
  domainId: string,
  accessToken: string,
  resourceId: string,
  body: UpdateProtectedResource,
): Promise<ProtectedResourcePrimaryData> =>
  getProtectedResourcesApi(accessToken).updateProtectedResource({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    protectedResource: resourceId,
    updateProtectedResource: body,
  });

export const getMcpServers = (domainId: string, accessToken: string, size = 10, page = 0, sort?: string): Promise<ProtectedResourcePage> =>
  getProtectedResourcesApi(accessToken).listProtectedResources({
    organizationId: 'DEFAULT',
    environmentId: 'DEFAULT',
    domain: domainId,
    size: size,
    page: page,
    type: 'MCP_SERVER',
    sort: sort,
  });

export const getMcpServer = (domainId: string, accessToken: string, id: string): Promise<ProtectedResourcePrimaryData> =>
  getProtectedResourcesApi(accessToken).findProtectedResource({
    organizationId: 'DEFAULT',
    environmentId: 'DEFAULT',
    domain: domainId,
    protectedResource: id,
    type: 'MCP_SERVER',
  });
