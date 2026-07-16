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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { Fixture } from '../../../test-fixture';
import { setupTargetDomain, TargetDomainFixture } from './target-domain-fixture';
import { setupHintDomain, HintDomainFixture } from './hint-domain-fixture';

export interface CibaFederationFixture extends Fixture {
  targetDomain: TargetDomainFixture;
  hintDomain: HintDomainFixture;
}

export const setupCibaFederationFixture = async (): Promise<CibaFederationFixture> => {
  const accessToken = await requestAdminAccessToken();

  const targetDomain = await setupTargetDomain(accessToken);
  const hintDomain = await setupHintDomain(accessToken, targetDomain);

  return {
    accessToken,
    targetDomain,
    hintDomain,
    cleanUp: async () => {
      await hintDomain.cleanUp();
      await targetDomain.cleanUp();
    },
  };
};
