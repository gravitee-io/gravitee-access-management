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

import * as process from 'node:process';
import { getExtensionApi } from '@management-commands/service/utils';

export const createExtensionGrant = (domainId, accessToken, body) =>
  getExtensionApi(accessToken).createExtensionGrant({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    newExtensionGrant: body,
  });

export const deleteExtensionGrant = (domainId, accessToken, extensionId) =>
  getExtensionApi(accessToken).deleteExtensionGrant({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    extensionGrant: extensionId,
  });

export const getExtensionGrant = (domainId, accessToken, extensionId) =>
  getExtensionApi(accessToken).getExtensionGrant({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    extensionGrant: extensionId,
  });

export const listExtensionGrant = (domainId, accessToken) =>
  getExtensionApi(accessToken).listExtensionGrants({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
  });

export const updateExtensionGrant = (domainId, accessToken, extensionId, updateExtension) =>
  getExtensionApi(accessToken).updateExtensionGrant({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    extensionGrant: extensionId,
    updateExtensionGrant: updateExtension
  });
