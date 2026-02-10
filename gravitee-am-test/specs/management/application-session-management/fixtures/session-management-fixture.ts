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

import { expect } from '@jest/globals';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import {
  createApplication,
  deleteApplication,
  getApplication,
  patchApplication,
} from '@management-commands/application-management-commands';
import { uniqueName } from '@utils-commands/misc';
import * as faker from 'faker';

export interface SessionManagementFixture {
  accessToken: string;
  domain: Domain;
  patchSessionSettings: (appId: string, inherited: boolean, persistent?: boolean) => Promise<Application>;
  getApplicationSettings: (appId: string) => Promise<Application>;
  createBrowserApp: (name?: string) => Promise<Application>;
  cleanUp: () => Promise<void>;
}

export const initFixture = async (): Promise<SessionManagementFixture> => {
  const accessToken = await requestAdminAccessToken();
  const { domain } = await setupDomainForTest(uniqueName('session-mgmt', true), { accessToken });

  const createdAppIds: string[] = [];

  const createBrowserApp = async (name?: string): Promise<Application> => {
    const appName = name ?? `${uniqueName('session-app', true)}`;
    const app = await createApplication(domain.id, accessToken, {
      name: appName,
      type: 'browser',
      description: faker.lorem.sentence(),
      redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
    });
    expect(app).toBeDefined();
    createdAppIds.push(app.id);
    return app;
  };

  const patchSessionSettings = async (appId: string, inherited: boolean, persistent?: boolean): Promise<Application> => {
    const cookieSettings: any = { inherited };
    if (!inherited && persistent !== undefined) {
      cookieSettings.session = { persistent };
    }
    return patchApplication(domain.id, accessToken, { settings: { cookieSettings } }, appId);
  };

  const getApplicationSettings = async (appId: string): Promise<Application> => {
    return getApplication(domain.id, accessToken, appId);
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
    patchSessionSettings,
    getApplicationSettings,
    createBrowserApp,
    cleanUp,
  };
};
