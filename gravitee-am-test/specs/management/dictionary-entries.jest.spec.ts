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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { createDictionary, getAllDictionaries, updateDictionaryEntries } from '@management-commands/dictionary-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { jira } from '@specs-utils/jira';
import { setup } from '../test-fixture';

setup(200000);

describe('i18n Dictionary', () => {
  let accessToken: string;
  let domainId: string;

  beforeAll(async () => {
    accessToken = await requestAdminAccessToken();
    const domain = await createDomain(accessToken, uniqueName('i18n-test', true), 'i18n regression test');
    domainId = domain.id;
  });

  afterAll(async () => {
    if (domainId && accessToken) {
      await safeDeleteDomain(domainId, accessToken);
    }
  });

  it(jira`should create a translation dictionary and add entries ${'AM-2184'}`, async () => {
    // Create dictionary
    const dictionary = await createDictionary(domainId, accessToken, {
      name: 'English',
      locale: 'en',
    });
    expect(dictionary.id).toEqual(expect.any(String));
    expect(dictionary.name).toEqual('English');
    expect(dictionary.locale).toEqual('en');

    // Add translation entries
    await updateDictionaryEntries(domainId, accessToken, dictionary.id, {
      'login.title': 'Custom Login Title',
      'login.button': 'Custom Sign In',
    });

    // Verify dictionaries exist
    const dictionaries = await getAllDictionaries(domainId, accessToken);
    expect(dictionaries.length).toBeGreaterThanOrEqual(1);
    const found = dictionaries.find((d: any) => d.id === dictionary.id);
    expect(found).not.toBeUndefined();
    expect(found.locale).toEqual('en');
  });
});
