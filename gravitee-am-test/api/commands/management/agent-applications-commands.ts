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

import { AgentApplicationPage } from '@management-models/AgentApplicationPage';
import { ResponseError } from '../../management/runtime';
import { getApplicationApi } from './service/utils';

export interface AgentListOptions {
  page?: number;
  size?: number;
  q?: string;
}

export const listAgentApplications = (
  domainId: string,
  accessToken: string,
  options?: AgentListOptions,
): Promise<AgentApplicationPage> =>
  getApplicationApi(accessToken).listAgentApplications({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    page: options?.page,
    size: options?.size,
    q: options?.q,
  });

export const listAgentApplicationsRaw = async (
  domainId: string,
  accessToken: string,
  options?: AgentListOptions,
): Promise<{ status: number }> => {
  try {
    await getApplicationApi(accessToken).listAgentApplicationsRaw({
      organizationId: process.env.AM_DEF_ORG_ID,
      environmentId: process.env.AM_DEF_ENV_ID,
      domain: domainId,
      page: options?.page,
      size: options?.size,
      q: options?.q,
    });
    return { status: 200 };
  } catch (err) {
    if (err instanceof ResponseError) {
      return { status: err.response.status };
    }
    throw err;
  }
};
