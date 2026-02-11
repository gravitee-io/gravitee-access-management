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

import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { NewApplication } from '@management-models/NewApplication';
import { PatchApplication } from '@management-models/PatchApplication';
import { PatchApplicationTypeTypeEnum } from '@management-models/PatchApplicationType';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import {
  createApplication,
  deleteApplication,
  patchApplication,
  updateApplicationType,
} from '@management-commands/application-management-commands';
import { uniqueName } from '@utils-commands/misc';

export interface ApplicationTypeFixture {
  accessToken: string;
  domain: Domain;
  createApp: (name: string, type: PatchApplicationTypeTypeEnum, metadata?: Record<string, string>) => Promise<Application>;
  setAppType: (appId: string, type: PatchApplicationTypeTypeEnum) => Promise<Application>;
  updateApp: (appId: string, body: PatchApplication) => Promise<Application>;
  cleanUp: () => Promise<void>;
}

export const initFixture = async (): Promise<ApplicationTypeFixture> => {
  const accessToken = await requestAdminAccessToken();
  const { domain } = await setupDomainForTest(uniqueName('app-type', true), { accessToken, waitForStart: false });

  const createdAppIds: string[] = [];

  const createApp = async (name: string, type: PatchApplicationTypeTypeEnum, metadata?: Record<string, string>): Promise<Application> => {
    const body: NewApplication = { name, type };
    if (metadata) {
      body.metadata = metadata;
    }
    const app = await createApplication(domain.id, accessToken, body);
    createdAppIds.push(app.id);
    return app;
  };

  const setAppType = async (appId: string, type: PatchApplicationTypeTypeEnum): Promise<Application> => {
    return updateApplicationType(domain.id, accessToken, appId, type);
  };

  const updateApp = async (appId: string, body: PatchApplication): Promise<Application> => {
    return patchApplication(domain.id, accessToken, body, appId);
  };

  const cleanUp = async () => {
    for (const appId of createdAppIds) {
      try {
        await deleteApplication(domain.id, accessToken, appId);
      } catch (err) {
        // ignore cleanup errors
      }
    }
    await safeDeleteDomain(domain?.id, accessToken);
  };

  return {
    accessToken,
    domain,
    createApp,
    setAppType,
    updateApp,
    cleanUp,
  };
};
