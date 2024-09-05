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

import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { expect } from '@jest/globals';

export const createTestApp = async (name, domain, accessToken, applicationType = 'web', body = {}) => {
    const creatAppSettings =
        applicationType !== 'service'
            ? {
                name: name,
                type: applicationType,
                clientId: `${name}-id`,
                redirectUris: body['settings']?.oauth?.redirectUris,
            }
            : {
                name: name,
                type: applicationType,
                clientId: `${name}-id`,
            };
  const application = await createApplication(domain.id, accessToken, creatAppSettings).then((app) =>
    updateApplication(domain.id, accessToken, body, app.id).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  expect(application).toBeDefined();
  expect(application.settings.oauth.clientId).toBeDefined();

  return application;
};
