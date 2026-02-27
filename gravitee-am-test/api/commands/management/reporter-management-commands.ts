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

import { NewReporter } from '@management-models/NewReporter';
import { UpdateReporter } from '@management-models/UpdateReporter';
import { Reporter } from '@management-models/Reporter';
import { getReporterApi } from './service/utils';

// Domain-level

export const createDomainReporter = (domainId: string, accessToken: string, body: NewReporter): Promise<Reporter> =>
  getReporterApi(accessToken).createDomainReporter({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    newReporter: body,
  });

export const getDomainReporter = (domainId: string, accessToken: string, reporterId: string): Promise<Reporter> =>
  getReporterApi(accessToken).getDomainReporter({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    reporter: reporterId,
  });

export const listDomainReporters = (domainId: string, accessToken: string): Promise<Array<Reporter>> =>
  getReporterApi(accessToken).listDomainReporters({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
  });

export const updateDomainReporter = (domainId: string, accessToken: string, reporterId: string, body: UpdateReporter): Promise<Reporter> =>
  getReporterApi(accessToken).updateDomainReporter({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    reporter: reporterId,
    updateReporter: body,
  });

export const deleteDomainReporter = (domainId: string, accessToken: string, reporterId: string): Promise<void> =>
  getReporterApi(accessToken).deleteDomainReporter({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    reporter: reporterId,
  });

// Org-level

export const createOrgReporter = (accessToken: string, body: NewReporter): Promise<Reporter> =>
  getReporterApi(accessToken).createOrgReporter({
    organizationId: process.env.AM_DEF_ORG_ID,
    newReporter: body,
  });

export const getOrgReporter = (accessToken: string, reporterId: string): Promise<Reporter> =>
  getReporterApi(accessToken).getOrgReporter({
    organizationId: process.env.AM_DEF_ORG_ID,
    reporterId,
  });

export const listOrgReporters = (accessToken: string): Promise<Array<Reporter>> =>
  getReporterApi(accessToken).getOrgReporters({
    organizationId: process.env.AM_DEF_ORG_ID,
  });

export const updateOrgReporter = (accessToken: string, reporterId: string, body: UpdateReporter): Promise<Reporter> =>
  getReporterApi(accessToken).updateOrgReporter({
    organizationId: process.env.AM_DEF_ORG_ID,
    reporterId,
    updateReporter: body,
  });

export const deleteOrgReporter = (accessToken: string, reporterId: string): Promise<void> =>
  getReporterApi(accessToken).deleteOrgReporter({
    organizationId: process.env.AM_DEF_ORG_ID,
    reporterId,
  });
