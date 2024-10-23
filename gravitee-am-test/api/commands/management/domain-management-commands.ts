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

import { getDomainApi, getDomainManagerUrl } from './service/utils';
import { Domain } from '../../management/models';
import { retryUntil } from '@utils-commands/retry';
import { getWellKnownOpenIdConfiguration } from '@gateway-commands/oauth-oidc-commands';

const request = require('supertest');

export const createDomain = (accessToken, name, description): Promise<Domain> =>
  getDomainApi(accessToken).createDomain({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    newDomain: {
      name: name,
      description: description,
    },
  });

export const deleteDomain = (domainId, accessToken): Promise<void> =>
  getDomainApi(accessToken).deleteDomain({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
  });

export const patchDomain = (domainId, accessToken, body): Promise<Domain> =>
  getDomainApi(accessToken).patchDomain({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    // domain in path param
    domain: domainId,
    // domain payload
    patchDomain: body,
  });

export const startDomain = (domainId, accessToken): Promise<Domain> => patchDomain(domainId, accessToken, { enabled: true });

export const getDomain = (domainId, accessToken): Promise<Domain> =>
  getDomainApi(accessToken).findDomain({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    // domain in path param
    domain: domainId,
  });

export const createAcceptAllDeviceNotifier = (domainId, accessToken) =>
  request(getDomainManagerUrl(null) + '/auth-device-notifiers')
    .post('')
    .set('Authorization', 'Bearer ' + accessToken)
    .send({
      type: 'http-am-authdevice-notifier',
      configuration:
        '{"endpoint":"http://localhost:8080/ciba/notify/accept-all","headerName":"Authorization","connectTimeout":5000,"idleTimeout":10000,"maxPoolSize":10}',
      name: 'Always OK notifier',
    })
    .expect(201);

export const getDomainFlows = (domainId, accessToken) =>
  getDomainApi(accessToken).listDomainFlows({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    // domain in path param
    domain: domainId,
  });

export const updateDomainFlows = (domainId, accessToken, flows) =>
  getDomainApi(accessToken).defineDomainFlows({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    // domain in path param
    domain: domainId,
    flow: flows,
  });

export const waitForDomainStart: (domain: Domain) => Promise<{ domain: Domain; oidcConfig: any }> = (domain: Domain) => {
  const start = Date.now();
  return retryUntil(
    () => getWellKnownOpenIdConfiguration(domain.hrid) as Promise<any>,
    (res) => res.status == 200,
    {
      timeoutMillis: 10000,
      onDone: () => console.log(`domain "${domain.hrid}" ready after ${(Date.now() - start) / 1000}s`),
      onRetry: () => console.debug(`domain "${domain.hrid}" not ready yet`),
    },
  ).then((response) => ({ domain, oidcConfig: response.text }));
};

export const waitForDomainSync = () => waitFor(10000);
export const waitFor = (duration) => new Promise((r) => setTimeout(r, duration));

export async function allowHttpLocalhostRedirects(domain: Domain, accessToken: string) {
  return patchDomain(domain.id, accessToken, {
    oidc: {
      clientRegistrationSettings: {
        allowLocalhostRedirectUri: true,
        allowHttpSchemeRedirectUri: true,
      },
    },
  });
}
