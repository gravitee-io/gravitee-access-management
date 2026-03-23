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

import * as faker from 'faker';
import { expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { createApplication, deleteApplication } from '@management-commands/application-management-commands';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../test-fixture';

export type BatchOptions = {
  buildName?: (index: number) => string;
  onAppCreated?: (app: Application, index: number) => void;
};

export type CreatedApplicationIdsByDomain = Record<string, string[]>;

export interface ApplicationsFixture extends Fixture {
  accessToken: string;
  primaryDomain: Domain;
  secondaryDomain: Domain;
  createdApplicationIds: CreatedApplicationIdsByDomain;
  recordApplicationForCleanup: (app?: Application, domainId?: string) => void;
  createApp: (domainId: string, name?: string, clientId?: string) => Promise<Application>;
  createAndTrackApplication: (domainId: string, payload: any) => Promise<Application>;
  seedBatch: (domainId: string, count: number, store: Application[], options?: BatchOptions) => Promise<void>;
  cleanUp: () => Promise<void>;
}

export const setupApplicationsFixture = async (): Promise<ApplicationsFixture> => {
  const accessToken = await requestAdminAccessToken();
  const primaryDomain = await setupDomainForTest(uniqueName('applications-primary', true), { accessToken }).then((it) => it.domain);
  const secondaryDomain = await setupDomainForTest(uniqueName('applications-secondary', true), { accessToken }).then((it) => it.domain);

  const createdApplicationIds: CreatedApplicationIdsByDomain = {};

  const recordApplicationForCleanup = (app?: Application, domainId?: string) => {
    if (app?.id && domainId) {
      if (!createdApplicationIds[domainId]) {
        createdApplicationIds[domainId] = [];
      }
      createdApplicationIds[domainId].push(app.id);
    }
  };

  const createApp = async (domainId: string, name?: string, clientId?: string): Promise<Application> => {
    const app: any = {
      name: name ?? faker.commerce.productName(),
      type: 'service',
      description: faker.lorem.paragraph(),
      metadata: {
        key1: faker.lorem.paragraph(),
        key2: faker.lorem.paragraph(),
        key3: faker.lorem.paragraph(),
        key4: faker.lorem.paragraph(),
      },
    };
    if (clientId) {
      app.clientId = clientId;
    }
    const createdApp = await createApplication(domainId, accessToken, app);
    recordApplicationForCleanup(createdApp, domainId);
    return createdApp;
  };

  const createAndTrackApplication = async (domainId: string, payload: any): Promise<Application> => {
    const createdApp = await createApplication(domainId, accessToken, payload);
    recordApplicationForCleanup(createdApp, domainId);
    return createdApp;
  };

  const seedBatch = async (domainId: string, count: number, store: Application[], options: BatchOptions = {}) => {
    for (let i = 0; i < count; i++) {
      const name = options.buildName?.(i);
      expect(name).toBeDefined();
      const createdApp = await createApp(domainId, name!);
      store.push(createdApp);
      options.onAppCreated?.(createdApp, i);
    }
  };

  const cleanUp = async () => {
    for (const domainId of Object.keys(createdApplicationIds)) {
      for (const appId of createdApplicationIds[domainId]) {
        try {
          await deleteApplication(domainId, accessToken, appId);
        } catch (err) {}
      }
    }
    if (primaryDomain?.id) {
      await safeDeleteDomain(primaryDomain.id, accessToken);
    }
    if (secondaryDomain?.id) {
      await safeDeleteDomain(secondaryDomain.id, accessToken);
    }
  };

  return {
    accessToken,
    primaryDomain,
    secondaryDomain,
    createdApplicationIds,
    recordApplicationForCleanup,
    createApp,
    createAndTrackApplication,
    seedBatch,
    cleanUp,
  };
};
