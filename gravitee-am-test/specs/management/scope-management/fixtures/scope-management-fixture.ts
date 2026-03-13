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
import { expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { setupDomainForTest, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';

export interface ScopeManagementFixture {
  domain: Domain;
  accessToken: string;
  cleanup: () => Promise<void>;
}

export const SCOPE_TEST = {
  DOMAIN_NAME_PREFIX: 'scope-mgmt',
  ICON_URI: 'https://gravitee.io/assets/images/gio_logo566072_gravitee.io.png',
} as const;

export const setupScopeManagementFixture = async (): Promise<ScopeManagementFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    const domainResult = await setupDomainForTest(uniqueName(SCOPE_TEST.DOMAIN_NAME_PREFIX, true), {
      accessToken,
      waitForStart: true,
    });
    domain = domainResult.domain;
    expect(domain).toBeDefined();
    expect(domain.id).toBeDefined();

    const cleanup = async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    };

    return {
      domain,
      accessToken,
      cleanup,
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup domain after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
