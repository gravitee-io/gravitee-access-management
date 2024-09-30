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

import { getDeviceIdentifiersApi } from './service/utils';
import * as process from 'node:process';

export const createDevice = (domainId, accessToken, body) =>
  getDeviceIdentifiersApi(accessToken).createDeviceIdentifier({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    deviceIdentifier: body,
  });

export const deleteDevice = (domainId, accessToken, deviceId) =>
  getDeviceIdentifiersApi(accessToken).deleteDeviceIdentifier({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    deviceIdentifier: deviceId,
  });

export const getDevice = (domainId, accessToken, deviceId) =>
  getDeviceIdentifiersApi(accessToken).getDeviceIdentifier({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    deviceIdentifier: deviceId,
  });

export const listDeviceIdentifiers = (domainId, accessToken) =>
  getDeviceIdentifiersApi(accessToken).listDeviceIdentifiers({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
  });

export const updateDevice = (domainId, accessToken, deviceId, updateDevice) =>
  getDeviceIdentifiersApi(accessToken).updateDeviceIdentifier({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    deviceIdentifier: deviceId,
    deviceIdentifier2: updateDevice,
  });
