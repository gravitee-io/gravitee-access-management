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

import { AuditPage } from '@management-models/AuditPage';

import { getAuditApi } from './service/utils';

export interface ListDomainAuditsOptions {
  type?: string;
  status?: string;
  user?: string;
  from?: number;
  to?: number;
  size?: number;
  page?: number;
}

export const listDomainAudits = (
  domainId: string,
  accessToken: string,
  options: ListDomainAuditsOptions = {},
): Promise<AuditPage> =>
  getAuditApi(accessToken).listDomainAudits({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    ...options,
  });

export const getDomainAudit = (domainId: string, accessToken: string, auditId: string): Promise<any> =>
  getAuditApi(accessToken).getDomainAudit({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    audit: auditId,
  });
