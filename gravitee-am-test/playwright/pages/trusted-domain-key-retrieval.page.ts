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
import { Locator } from '@playwright/test';
import { BasePage } from './base.page';

/** Page object for Domain > Settings > Security > Trusted Domains > Key retrieval. */
export class TrustedDomainKeyRetrievalPage extends BasePage {
  async navigateTo(domainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/trust-domains/key-retrieval`);
  }

  get allowUnsecuredHttpUriToggle(): Locator {
    return this.page.getByRole('switch', { name: /Allow unsecured HTTP JWKS URLs/i });
  }

  get allowPrivateIpAddressToggle(): Locator {
    return this.page.getByRole('switch', { name: /Allow JWKS URLs resolving to private IP addresses/i });
  }

  get fetchTimeoutInput(): Locator {
    return this.page.locator('input[name="fetchTimeoutMs"]');
  }

  get cacheTtlInput(): Locator {
    return this.page.locator('input[name="cacheTtlSeconds"]');
  }

  get saveButton(): Locator {
    return this.page.locator('[data-testid="saveButton"]');
  }
}
