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

import { AuthorizationEngineApi } from '@management-apis/AuthorizationEngineApi';
import { Configuration } from '../../management/runtime';
import { AuthorizationEngine } from '../../management/models';

const getAuthorizationEngineApi = (accessToken: string): AuthorizationEngineApi => {
  return new AuthorizationEngineApi(
    new Configuration({
      basePath: process.env.AM_MANAGEMENT_ENDPOINT,
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    }),
  );
};

export interface AuthorizationEngineConfig {
  type: string;
  name: string;
  configuration: string;
}

export async function createAuthorizationEngine(
  domainId: string,
  accessToken: string,
  config: AuthorizationEngineConfig,
): Promise<AuthorizationEngine> {
  return getAuthorizationEngineApi(accessToken).createAuthorizationEngine({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    newAuthorizationEngine: config,
  });
}

export async function deleteAuthorizationEngine(domainId: string, engineId: string, accessToken: string): Promise<void> {
  return getAuthorizationEngineApi(accessToken).deleteAuthorizationEngine({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    engineId: engineId,
  });
}
