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
import fetch from 'cross-fetch';
import * as faker from 'faker';
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, deleteDomain, startDomain, waitFor } from '@management-commands/domain-management-commands';
import {
  createDevice,
  deleteDevice,
  getDevice,
  listDeviceIdentifiers,
  updateDevice,
} from '@management-commands/device-management-commands';

global.fetch = fetch;

let accessToken: any;
let domain: any;
let device: any;
let deviceProId: any;

jest.setTimeout(200000);

beforeAll(async () => {
  const adminTokenResponse = await requestAdminAccessToken();
  accessToken = adminTokenResponse.body.access_token;
  expect(accessToken).toBeDefined();

  const createdDomain = await createDomain(accessToken, 'domain-certificate', faker.company.catchPhraseDescriptor());
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  const domainStarted = await startDomain(createdDomain.id, accessToken);
  expect(domainStarted).toBeDefined();
  expect(domainStarted.id).toEqual(createdDomain.id);

  domain = domainStarted;
});

describe('when creating device identifier', () => {
  it('must create new V3 community device identifier: ', async () => {
    const body = {
      configuration: '{}',
      name: 'device V3 community',
      type: 'fingerprintjs-v3-community-device-identifier',
    };
    const createdDevice = await createDevice(domain.id, accessToken, body);
    expect(createdDevice.id).toBeDefined();
    device = createdDevice;
  });

  it('must create new V3 community device identifier: ', async () => {
    const body = {
      configuration: '{"browserToken":"ABCDEF1234","region":"eu"}',
      name: 'device V3 pro',
      type: 'fingerprintjs-v3-pro-device-identifier',
    };
    const createdDevice = await createDevice(domain.id, accessToken, body);
    expect(createdDevice.id).toBeDefined();
    deviceProId = createdDevice.id;
  });
});
describe('after creating device identifier', () => {
  it('must list all devices', async () => {
    const devices = await listDeviceIdentifiers(domain.id, accessToken);
    expect(devices.length).toEqual(2);
  });

  it('must find device', async () => {
    const foundDevice = await getDevice(domain.id, accessToken, device.id);
    expect(foundDevice).toBeDefined();
    expect(foundDevice.id).toEqual(device.id);
  });

  it('must update device', async () => {
    const updatedDevice = await updateDevice(domain.id, accessToken, device.id, { ...device, name: 'device V3 community new name' });
    expect(updatedDevice.name === device.name).toBeFalsy();
    device = updatedDevice;
  });

  it('Must delete device identifier', async () => {
    await deleteDevice(domain.id, accessToken, device.id);
    await deleteDevice(domain.id, accessToken, deviceProId);
    const devices = await listDeviceIdentifiers(domain.id, accessToken);
    expect(devices.length).toEqual(0);
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await deleteDomain(domain.id, accessToken);
  }
});
