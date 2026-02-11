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

import { getApplicationApi } from './service/utils';
import { ApplicationPage } from '../../management/models/ApplicationPage';
import { PatchApplicationTypeTypeEnum } from '../../management/models/PatchApplicationType';

export type ApplicationListOptions = {
  page?: number;
  size?: number;
  q?: string;
};

export const createApplication = (domainId, accessToken, body) =>
  getApplicationApi(accessToken).createApplication({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    newApplication: body,
  });

export const getApplication = (domainId, accessToken, applicationId) =>
  getApplicationApi(accessToken).findApplication({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    application: applicationId,
  });

export const listApplications = (domainId: string, accessToken: string, options?: ApplicationListOptions): Promise<ApplicationPage> =>
  getApplicationApi(accessToken).listApplications({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    page: options?.page,
    size: options?.size,
    q: options?.q,
  });

export const patchApplication = (domainId, accessToken, body, applicationId) =>
  getApplicationApi(accessToken).patchApplication({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    application: applicationId,
    patchApplication: body,
  });

export const updateApplication = (domainId, accessToken, body, applicationId) =>
  getApplicationApi(accessToken).updateApplication({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    application: applicationId,
    patchApplication: body,
  });

export const deleteApplication = (domainId, accessToken, applicationId) =>
  getApplicationApi(accessToken).deleteApplication({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    application: applicationId,
  });

export const renewApplicationSecrets = (domainId, accessToken, applicationId, secretId) =>
  getApplicationApi(accessToken).renewClientSecret({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    application: applicationId,
    secret: secretId,
  });

export const getApplicationFlows = (domainId, accessToken, applicationId) =>
  getApplicationApi(accessToken).listAppFlows({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    application: applicationId,
  });

export const updateApplicationFlows = (domainId, accessToken, applicationId, flows) =>
  getApplicationApi(accessToken).defineAppFlows({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    application: applicationId,
    flow: flows,
  });

export const updateApplicationType = (domainId, accessToken, applicationId, type: PatchApplicationTypeTypeEnum) =>
  getApplicationApi(accessToken).updateApplicationType({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    application: applicationId,
    patchApplicationType: { type },
  });
