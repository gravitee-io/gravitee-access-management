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
    createDomain,
    patchDomain,
    safeDeleteDomain,
    startDomain,
    waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

export interface MagicLinkFixture extends Fixture {
    domain: Domain;
    enableMagicLink: () => Promise<Domain>;
    disableMagicLink: () => Promise<Domain>;
}

export const setupFixture = async (): Promise<MagicLinkFixture> => {
    const accessToken = await requestAdminAccessToken();
    const domain = await createDomain(accessToken, uniqueName('magic_link', true), 'Description');
    await startDomain(domain.id, accessToken);
    await waitForDomainStart(domain);
    return {
        accessToken: accessToken,
        domain: domain,
        enableMagicLink: async () => {
            return await patchDomain(domain.id, accessToken, {
                loginSettings: {
                    magicLinkAuthEnabled: true,
                },
            });
        },
        disableMagicLink: async () => {
            return await patchDomain(domain.id, accessToken, {
                loginSettings: {
                    magicLinkAuthEnabled: false,
                },
            });
        },
        cleanUp: async () => {
            if (domain?.id && accessToken) {
                await safeDeleteDomain(domain.id, accessToken);
            }
        },
    };
};
