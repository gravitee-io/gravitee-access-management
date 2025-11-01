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
import { getApplicationApi } from '@management-commands/service/utils';

export const createClientSecret = (domainId, accessToken, application, newClientSecret) =>
  getApplicationApi(accessToken).createSecret({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    application: application,
    newClientSecret: newClientSecret,
  });

export const renewClientSecret = (domainId, accessToken, application, secret) =>
  getApplicationApi(accessToken).renewClientSecret({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    application: application,
    secret: secret,
  });

export const deleteClientSecret = (domainId, accessToken, application, secret) =>
  getApplicationApi(accessToken).deleteClientSecret({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    application: application,
    secret: secret,
  });

export const listClientSecrets = (domainId, accessToken, application) =>
  getApplicationApi(accessToken).listSecrets({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    application: application,
  });
