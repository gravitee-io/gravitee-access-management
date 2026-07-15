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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest, patchDomain, getDomain } from '@management-commands/domain-management-commands';
import {
  createApplication,
  deleteApplication,
  getApplication,
  patchApplication,
  updateApplicationType,
} from '@management-commands/application-management-commands';
import { uniqueName } from '@utils-commands/misc';

export interface FeatureSettingsFixture {
  accessToken: string;
  domain: Domain;
  app: Application;
  patchDomainSettings: (settings: any) => Promise<Domain>;
  fetchDomain: () => Promise<Domain>;
  patchApp: (body: PatchApplication) => Promise<Application>;
  fetchApp: () => Promise<Application>;
  cleanUp: () => Promise<void>;
}

export const initFixture = async (): Promise<FeatureSettingsFixture> => {
  const accessToken = await requestAdminAccessToken();
  const { domain } = await setupDomainForTest(uniqueName('feature-settings', true), { accessToken, waitForStart: false });

  // Create as SERVICE first (no redirect URIs required on creation), then switch to WEB
  const app = await createApplication(domain.id, accessToken, {
    name: uniqueName('feature-settings-app', true),
    type: 'SERVICE',
  });
  await updateApplicationType(domain.id, accessToken, app.id, 'WEB');
  // WEB type requires at least one redirect URI for subsequent patches
  await patchApplication(domain.id, accessToken, { settings: { oauth: { redirectUris: ['https://callback.example.com'] } } }, app.id);

  const patchDomainSettings = (settings: any): Promise<Domain> => patchDomain(domain.id, accessToken, settings);

  const fetchDomain = (): Promise<Domain> => getDomain(domain.id, accessToken);

  const patchApp = (body: PatchApplication): Promise<Application> => patchApplication(domain.id, accessToken, body, app.id);

  const fetchApp = (): Promise<Application> => getApplication(domain.id, accessToken, app.id);

  const cleanUp = async (): Promise<void> => {
    try {
      await deleteApplication(domain.id, accessToken, app.id);
    } catch (_) {
      // ignore cleanup errors
    }
    await safeDeleteDomain(domain?.id, accessToken);
  };

  return { accessToken, domain, app, patchDomainSettings, fetchDomain, patchApp, fetchApp, cleanUp };
};
