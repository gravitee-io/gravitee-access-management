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
import { PatchApplication } from '@management-models/PatchApplication';
import { PatchApplicationTypeTypeEnum } from '@management-models/PatchApplicationType';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import {
  createApplication,
  deleteApplication,
  getApplication,
  patchApplication,
  updateApplicationType,
} from '@management-commands/application-management-commands';
import { uniqueName } from '@utils-commands/misc';

export interface ApplicationGeneralSettingsFixture {
  accessToken: string;
  domain: Domain;
  app: Application;
  patchApp: (body: PatchApplication) => Promise<Application>;
  fetchApp: () => Promise<Application>;
  setAppType: (type: PatchApplicationTypeTypeEnum) => Promise<Application>;
  cleanUp: () => Promise<void>;
}

export const initFixture = async (): Promise<ApplicationGeneralSettingsFixture> => {
  const accessToken = await requestAdminAccessToken();
  const { domain } = await setupDomainForTest(uniqueName('app-gen-settings', true), { accessToken, waitForStart: false });

  // Create as SERVICE first (no redirect URIs required on creation), then switch to WEB
  const app = await createApplication(domain.id, accessToken, {
    name: uniqueName('gen-settings-app', true),
    type: 'SERVICE',
  });
  await updateApplicationType(domain.id, accessToken, app.id, 'WEB');

  const patchApp = (body: PatchApplication): Promise<Application> => patchApplication(domain.id, accessToken, body, app.id);

  const fetchApp = (): Promise<Application> => getApplication(domain.id, accessToken, app.id);

  const setAppType = (type: PatchApplicationTypeTypeEnum): Promise<Application> =>
    updateApplicationType(domain.id, accessToken, app.id, type);

  const cleanUp = async (): Promise<void> => {
    try {
      await deleteApplication(domain.id, accessToken, app.id);
    } catch (_) {
      // ignore cleanup errors
    }
    await safeDeleteDomain(domain?.id, accessToken);
  };

  return { accessToken, domain, app, patchApp, fetchApp, setAppType, cleanUp };
};
