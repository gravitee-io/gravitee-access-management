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
import { getBotDetecionApi } from '@management-commands/service/utils';
import process from 'node:process';

export const createBotDetection = (domainId, accessToken, body) =>
  getBotDetecionApi(accessToken).createBotDetection({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    detection: body,
  });

export const updateBotDetection = (domainId, accessToken, botDetectionId, body) =>
  getBotDetecionApi(accessToken).updateBotDetection({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    botDetection: botDetectionId,
    identity: body,
  });

export const getBotDetection = (domainId, accessToken, botDetectionId) =>
  getBotDetecionApi(accessToken).getBotDetection({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    botDetection: botDetectionId,
  });

export const listBotDetection = (domainId, accessToken) =>
  getBotDetecionApi(accessToken).listBotDetections({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
  });

export const deleteBotDetection = (domainId, accessToken, botDetectionId) =>
  getBotDetecionApi(accessToken).deleteBotDetection({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    botDetection: botDetectionId,
  });
