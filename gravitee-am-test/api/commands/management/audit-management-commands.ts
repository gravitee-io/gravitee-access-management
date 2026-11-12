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
import { getDomainManagerUrl } from './service/utils';
import { retryUntil } from '@utils-commands/retry';

const request = require('supertest');

export const listDomainAudits = (domainId: string, accessToken: string, type: string) =>
  request(getDomainManagerUrl(domainId) + '/audits')
    .get('')
    .query({ type })
    .set('Authorization', 'Bearer ' + accessToken)
    .expect(200)
    .then((response) => response.body.data ?? []);

export const waitForDomainAudit = (domainId: string, accessToken: string, type: string, targetId: string) =>
  retryUntil(
    () => listDomainAudits(domainId, accessToken, type),
    (audits) => audits.some((audit) => audit.target?.id === targetId),
    { timeoutMillis: 20000, intervalMillis: 500 },
  ).then((audits) => audits.find((audit) => audit.target?.id === targetId));
