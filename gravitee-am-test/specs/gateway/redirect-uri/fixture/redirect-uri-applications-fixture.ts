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
import { Application } from '@management-models/Application';
import { createApplication, patchApplication } from '@management-commands/application-management-commands';
import faker from 'faker';

export const failingELParam = {
  callback1: 'https://callback/',
  callback2: 'https://callback2/',
  redirect_uris: [
    `https://callback/?param={#context.attributes['nonexisting'].applicationType}`,
    `https://callback2/?param={#noExistingProperty}&param3=test&param2={#context.attributes['client'].applicationType}`,
  ],
};

export const normal = {
  callback1: 'https://callback/',
  redirect_uris: [`https://callback/?param=test`],
};

export const singleParam = {
  callback1: 'https://callback/',
  callback2: 'https://callback2/',
  redirect_uris: [
    `https://callback/?param={#context.attributes['client'].applicationType}`,
    `https://callback2/?{#context.attributes['client'].applicationType}`,
  ],
};

export const multiParam = {
  callback1: 'https://callback/',
  redirect_uris: [
    `https://callback/?param={#context.attributes['client'].applicationType}&param2={#context.attributes['client'].applicationType}`,
  ],
};

export const multiParamAndRegular = {
  callback1: 'https://callback/',
  redirect_uris: [
    `https://callback/?param={#context.attributes['client'].applicationType}&param3=test&param2={#context.attributes['client'].applicationType}`,
    `https://callback2/?param={#context.attributes['client'].applicationType}&param3=test&param2={#context.attributes['client'].applicationType}`,
  ],
};

export interface RedirectUriApplications {
  failingELParam: Application;
  normal: Application;
  singleParam: Application;
  multiParam: Application;
  multiParamAndRegular: Application;
}

export const createApps = async (domainId: string, customIdpId: string, accessToken: string): Promise<RedirectUriApplications> => {
  const singleParamApp = await createApp(domainId, accessToken, customIdpId, singleParam.redirect_uris);
  const applicationMultiELParam = await createApp(domainId, accessToken, customIdpId, multiParam.redirect_uris);
  const applicationMultiELParamAndRegular = await createApp(domainId, accessToken, customIdpId, multiParamAndRegular.redirect_uris);
  const applicationFailingELParam = await createApp(domainId, accessToken, customIdpId, failingELParam.redirect_uris);
  const normalApp = await createApp(domainId, accessToken, customIdpId, normal.redirect_uris);

  return {
    failingELParam: applicationFailingELParam,
    multiParam: applicationMultiELParam,
    multiParamAndRegular: applicationMultiELParamAndRegular,
    normal: normalApp,
    singleParam: singleParamApp,
  };
};

const createApp = async (domainId: string, customIdpId: string, accessToken: string, redirectUris: string[]): Promise<Application> => {
  return await createApplication(domainId, accessToken, {
    name: faker.commerce.productName(),
    type: 'WEB',
    description: faker.lorem.paragraph(),
    redirectUris: redirectUris,
  }).then((app) =>
    patchApplication(
      domainId,
      accessToken,
      {
        identityProviders: new Set([{ identity: customIdpId, priority: 0 }]),
      },
      app.id,
    ),
  );
};
