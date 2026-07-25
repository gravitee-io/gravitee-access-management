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

export interface AuditPage {
  data: any[];
  currentPage: number;
  totalCount: number;
}

const domainAuditsUrl = (domainId: string, options: ListDomainAuditsOptions): string => {
  const params = new URLSearchParams();
  Object.entries(options).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      params.append(key, String(value));
    }
  });
  const query = params.toString();
  return (
    `${process.env.AM_MANAGEMENT_ENDPOINT}/organizations/${process.env.AM_DEF_ORG_ID}` +
    `/environments/${process.env.AM_DEF_ENV_ID}/domains/${domainId}/audits${query ? `?${query}` : ''}`
  );
};

/**
 * Called directly rather than through the generated SDK: the endpoint returns a page object, while
 * the generated client is typed for a bare array and maps over the response.
 */
export const listDomainAudits = async (
  domainId: string,
  accessToken: string,
  options: ListDomainAuditsOptions = {},
): Promise<AuditPage> => {
  const response = await fetch(domainAuditsUrl(domainId, options), {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) {
    throw new Error(`Listing audits for domain ${domainId} failed with ${response.status}: ${await response.text()}`);
  }
  return response.json();
};

export const getDomainAudit = (domainId: string, accessToken: string, auditId: string): Promise<any> =>
  getAuditApi(accessToken).getDomainAudit({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    audit: auditId,
  });
