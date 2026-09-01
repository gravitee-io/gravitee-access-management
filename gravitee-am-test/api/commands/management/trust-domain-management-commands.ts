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

import { getTrustDomainApi } from './service/utils';
import { NewTrustDomain } from '@management-models/NewTrustDomain';
import { UpdateTrustDomain } from '@management-models/UpdateTrustDomain';
import { TrustDomain } from '@management-models/TrustDomain';

export const createTrustDomain = (domainId: string, accessToken: string, body: NewTrustDomain): Promise<TrustDomain> =>
  getTrustDomainApi(accessToken).createTrustDomain({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    newTrustDomain: body,
  });

export const updateTrustDomain = (
  domainId: string,
  accessToken: string,
  trustDomainId: string,
  body: UpdateTrustDomain,
): Promise<TrustDomain> =>
  getTrustDomainApi(accessToken).updateTrustDomain({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    trustDomainId: trustDomainId,
    updateTrustDomain: body,
  });

export const getTrustDomain = (domainId: string, accessToken: string, trustDomainId: string): Promise<TrustDomain> =>
  getTrustDomainApi(accessToken).getTrustDomain({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    trustDomainId: trustDomainId,
  });

export const listTrustDomains = (domainId: string, accessToken: string): Promise<Array<TrustDomain>> =>
  getTrustDomainApi(accessToken).listTrustDomains({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
  });

export const deleteTrustDomain = (domainId: string, accessToken: string, trustDomainId: string): Promise<void> =>
  getTrustDomainApi(accessToken).deleteTrustDomain({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    trustDomainId: trustDomainId,
  });
