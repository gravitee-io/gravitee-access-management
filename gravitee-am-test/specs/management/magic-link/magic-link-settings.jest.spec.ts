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
import { afterAll, beforeAll, expect } from '@jest/globals';
import { MagicLinkFixture, setupFixture } from './fixture/magic-link-fixture';
import { getDomain, patchDomain } from '@management-commands/domain-management-commands';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: MagicLinkFixture;

beforeAll(async () => {
    fixture = await setupFixture();
});

afterAll(async () => {
    if (fixture) {
        await fixture.cleanUp();
    }
});

describe('Magic Link Authentication - Domain Settings', () => {
    it('should enable Magic Link', async () => {
        // Given: A domain exists
        expect(fixture.domain).toBeDefined();

        const patchedDomain = await fixture.enableMagicLink();

        // Then: Magic Link is enabled
        expect(patchedDomain).toBeDefined();
        expect(patchedDomain.loginSettings).toBeDefined();
        expect(patchedDomain.loginSettings?.magicLinkAuthEnabled).toBe(true);
    });

    it('should disable Magic Link', async () => {
        // Given: Magic Link is enabled
        await fixture.enableMagicLink();
        await fixture.disableMagicLink();

        // Then: Magic Link is disabled (verify by getting domain)
        const retrievedDomain = await getDomain(fixture.domain.id, fixture.accessToken);
        expect(retrievedDomain).toBeDefined();
        expect(retrievedDomain.loginSettings).toBeDefined();
        expect(retrievedDomain.loginSettings?.magicLinkAuthEnabled).toBe(false);
    });

    it('should persist Magic Link settings', async () => {
        // Given: Magic Link is enabled
        await fixture.enableMagicLink();

        // When: Domain is retrieved
        const retrievedDomain = await getDomain(fixture.domain.id, fixture.accessToken);

        // Then: Magic Link setting is persisted
        expect(retrievedDomain).toBeDefined();
        expect(retrievedDomain.loginSettings).toBeDefined();
        expect(retrievedDomain.loginSettings?.magicLinkAuthEnabled).toBe(true);
    });

    it('should allow combining magic link authentication with other login settings', async () => {
        // Given: A domain exists
        expect(fixture.domain).toBeDefined();

        // When: Admin enables Magic Link along with other login settings
        await patchDomain(fixture.domain.id, fixture.accessToken, {
            loginSettings: {
                magicLinkAuthEnabled: true,
                passwordlessEnabled: true,
                forgotPasswordEnabled: true,
            },
        });

        // Then: All settings are applied (verify by getting domain)
        const retrievedDomain = await getDomain(fixture.domain.id, fixture.accessToken);
        expect(retrievedDomain).toBeDefined();
        expect(retrievedDomain.loginSettings).toBeDefined();
        expect(retrievedDomain.loginSettings?.magicLinkAuthEnabled).toBe(true);
        expect(retrievedDomain.loginSettings?.passwordlessEnabled).toBe(true);
        expect(retrievedDomain.loginSettings?.forgotPasswordEnabled).toBe(true);
    });
});
