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
import { afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, setupDomainForTest, startDomain } from '@management-commands/domain-management-commands';
import { createTheme, deleteTheme, getAllThemes, getTheme, updateTheme } from '@management-commands/theme-management-commands';
import { getAllDictionaries } from '@management-commands/dictionary-management-commands';
import { ResponseError } from '../../api/management/runtime';
import { uniqueName } from '@utils-commands/misc';

global.fetch = fetch;

jest.setTimeout(200000);

let accessToken;
let domain;
let theme;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  domain = await setupDomainForTest(uniqueName('themes', true), { accessToken, waitForStart: false }).then((it) => it.domain);
});

async function testCreate() {
  let name = faker.address.country();
  let locale = faker.address.countryCode();
  const created = await createTheme(domain.id, accessToken, { name: name, locale: locale });
  expect(created).toBeDefined();
  expect(created.id).toBeDefined();
  return created;
}

describe('Testing themes api...', () => {
  describe('When creating many themes', () => {
    it('must only create one theme maximum ', async () => {
      theme = await testCreate();
      await expect(testCreate()).rejects.toMatchObject({
        response: { status: 400 }
      });
    });

    it('should find only one theme', async () => {
      const themes = await getAllThemes(domain.id, accessToken);
      expect(themes).toHaveLength(1);
      await deleteTheme(domain.id, accessToken, theme.id);
    });
  });

  describe('When creating a theme', () => {
    it('should create a theme ', async () => {
      theme = await testCreate();
    });
    it('should find a given theme', async () => {
      const found = await getTheme(domain.id, accessToken, theme.id);
      expect(found).toBeDefined();
      expect(found.id).toEqual(theme.id);
      await deleteTheme(domain.id, accessToken, theme.id);
    });
  });

  describe('When updating a theme', () => {
    it("should update a theme's requested properties", async () => {
      theme = await testCreate();
      const beforeUpdate = await getTheme(domain.id, accessToken, theme.id);
      let updatePrimaryButtonColorHex = '#fafafa';
      await updateTheme(domain.id, accessToken, beforeUpdate.id, {
        primaryButtonColorHex: updatePrimaryButtonColorHex,
      });
      const afterUpdate = await getTheme(domain.id, accessToken, beforeUpdate.id);
      expect(afterUpdate.id).toEqual(beforeUpdate.id);
      expect(afterUpdate.primaryButtonColorHex).toEqual(updatePrimaryButtonColorHex);
      await deleteTheme(domain.id, accessToken, theme.id);
    });
  });

  describe('When deleting a theme', () => {
    it('should delete a theme', async () => {
      theme = await testCreate();
      await deleteTheme(domain.id, accessToken, theme.id);

      const dictionaries = await getAllDictionaries(domain.id, accessToken);
      dictionaries.forEach((dict) => expect(dict.id).not.toEqual(theme.id));
    });
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});
