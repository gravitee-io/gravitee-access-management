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

/** Page object for Domain > Settings > Security > Trusted Domains list. */
export class TrustedDomainListPage extends BasePage {
  async navigateTo(domainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/trust-domains/domains`);
  }

  get addButton(): Locator {
    return this.page.locator('[data-testid="addTrustDomainButton"]');
  }

  get trustDomainTable(): Locator {
    return this.page.locator('[data-testid="trustDomainsTable"]');
  }

  get trustDomainRows(): Locator {
    return this.page.locator('[data-testid="trustDomainsTable"] .datatable-body-row');
  }

  get emptyState(): Locator {
    return this.page.locator('[data-testid="trustDomainsEmptyState"]');
  }

  get domainsTab(): Locator {
    return this.page.locator('a[mat-tab-link]').filter({ hasText: /^\s*Domains\s*$/ });
  }

  get keyRetrievalTab(): Locator {
    return this.page.locator('a[mat-tab-link]').filter({ hasText: /key retrieval/i });
  }

  async openKeyRetrievalTab(): Promise<void> {
    await this.keyRetrievalTab.click();
    await this.waitForReady();
  }

  async getTrustDomainCount(): Promise<number> {
    return this.trustDomainRows.count();
  }

  usageOf(index: number): Locator {
    return this.trustDomainRows.nth(index).locator('[data-testid="trustDomainUsage"]');
  }

  rowByName(name: string): Locator {
    return this.trustDomainRows.filter({ hasText: name });
  }

  async clickAddTrustDomain(): Promise<void> {
    await this.addButton.click();
    await this.waitForReady();
  }

  async clickEditTrustDomain(index: number): Promise<void> {
    await this.trustDomainRows.nth(index).locator('[data-testid="trustDomainSettingsAction"]').click();
    await this.waitForReady();
  }

  async clickDeleteTrustDomain(index: number): Promise<void> {
    await this.trustDomainRows.nth(index).locator('[data-testid="trustDomainDeleteAction"]').click();
  }
}
