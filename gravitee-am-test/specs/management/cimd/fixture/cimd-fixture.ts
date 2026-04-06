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
import { Domain } from '@management-models/Domain';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  getDomain,
  patchDomain,
  safeDeleteDomain,
  setupDomainForTest,
} from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

export interface CimdFixture extends Fixture {
  domain: Domain;
  accessToken: string;
  patchCimdSettings: (cimdSettingsPatch: object) => Promise<Domain>;
  getDomain: () => Promise<Domain>;
}

export const setupFixture = async (): Promise<CimdFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;

  try {
    const result = await setupDomainForTest(uniqueName('cimd', true), {
      accessToken,
      waitForStart: false,
    });
    domain = result.domain;

    return {
      accessToken,
      domain,
      patchCimdSettings: (cimdSettingsPatch: object): Promise<Domain> =>
        patchDomain(domain.id, accessToken, {
          oidc: {
            cimdSettings: cimdSettingsPatch,
          },
        }),
      getDomain: (): Promise<Domain> => getDomain(domain.id, accessToken),
      cleanUp: async () => {
        if (domain?.id && accessToken) {
          await safeDeleteDomain(domain.id, accessToken);
        }
      },
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
