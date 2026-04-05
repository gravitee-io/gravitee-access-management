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
import { createDomain, getDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { jira } from '@specs-utils/jira';
import { setup } from '../test-fixture';

setup(200000);

describe('Domain Create', () => {
  let accessToken: string;
  let domainId: string;

  beforeAll(async () => {
    accessToken = await requestAdminAccessToken();
  });

  afterAll(async () => {
    if (domainId && accessToken) {
      await safeDeleteDomain(domainId, accessToken);
    }
  });

  it(jira`should create a security domain ${'AM-2217'}`, async () => {
    const name = uniqueName('domain-create-test', true);
    const description = 'Test domain created by regression test AM-2217';

    const domain = await createDomain(accessToken, name, description);
    domainId = domain.id;

    expect(domain.id).toEqual(expect.any(String));
    expect(domain.name).toEqual(name);
    expect(domain.description).toEqual(description);

    // Verify via GET
    const fetched = await getDomain(domain.id, accessToken);
    expect(fetched.id).toEqual(domain.id);
    expect(fetched.name).toEqual(name);
    expect(fetched.description).toEqual(description);
  });
});
