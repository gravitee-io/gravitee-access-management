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

import { afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, setupDomainForTest, startDomain } from '@management-commands/domain-management-commands';
import { delay } from '@utils-commands/misc';
import { setup } from '../test-fixture';
import {
  createBotDetection,
  deleteBotDetection,
  getBotDetection,
  listBotDetection,
  updateBotDetection,
} from '@management-commands/bot-detection-management-commands';

setup(200000);

let accessToken;
let domain;
let botDetection;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  domain = await setupDomainForTest('bot-detection-domain', { waitForStart: true }).then((started) => started.domain);
});

describe('CRUD Bot detection', () => {
  it('should create bot detection', async () => {
    const body = {
      configuration:
        '{"siteKey":"abcd","secretKey":"abcd","serviceUrl":"https://www.google.com/recaptcha/api/siteverify","tokenParameterName":"X-Recaptcha-Token","minScore":0.5}',
      detectionType: 'CAPTCHA',
      name: 'bot-detection',
      type: 'google-recaptcha-v3-am-bot-detection',
    };
    const createdBotDetection = await createBotDetection(domain.id, accessToken, body);
    expect(createdBotDetection.id).toBeDefined();
    botDetection = createdBotDetection;
  });

  it('should update bot detection', async () => {
    const body = {
      configuration:
        '{"siteKey":"cdef","secretKey":"abcd","serviceUrl":"https://www.google.com/recaptcha/api/siteverify","tokenParameterName":"X-Recaptcha-Token","minScore":0.5}',
      detectionType: 'CAPTCHA',
      name: 'bot-detection',
      type: 'google-recaptcha-v3-am-bot-detection',
    };
    const updatedBotDetection = await updateBotDetection(domain.id, accessToken, botDetection.id, body);
    expect(updatedBotDetection.id).toEqual(botDetection.id);
    expect(updatedBotDetection.configuration).toContain('cdef');
    botDetection = updatedBotDetection;
  });

  it('should get bot detection', async () => {
    const receivedBotDetection = await getBotDetection(domain.id, accessToken, botDetection.id);
    expect(receivedBotDetection.id).toEqual(botDetection.id);
  });

  it('should list bot detection', async () => {
    const receivedBotDetection = await listBotDetection(domain.id, accessToken);
    expect(receivedBotDetection.length).toEqual(1);
  });

  it('should remove', async () => {
    await deleteBotDetection(domain.id, accessToken, botDetection.id);
    const receivedBotDetection = await listBotDetection(domain.id, accessToken);
    expect(receivedBotDetection.length).toEqual(0);
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});
