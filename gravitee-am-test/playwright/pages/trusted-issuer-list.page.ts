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

/** Page object for Domain > Settings > Token Exchange > Trusted Issuers list. */
export class TrustedIssuerListPage extends BasePage {
  async navigateTo(domainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/token-exchange/trusted-issuers`);
  }

  get addButton(): Locator {
    return this.page.locator('[data-testid="addIssuerButton"]');
  }

  get issuerTable(): Locator {
    return this.page.locator('[data-testid="issuersTable"]');
  }

  get issuerRows(): Locator {
    return this.page.locator('[data-testid="issuersTable"] .datatable-body-row');
  }

  get emptyState(): Locator {
    return this.page.locator('[data-testid="issuersEmptyState"]');
  }

  async getIssuerCount(): Promise<number> {
    return this.issuerRows.count();
  }

  async clickAddIssuer(): Promise<void> {
    await this.addButton.click();
    await this.waitForReady();
  }

  async clickEditIssuer(index: number): Promise<void> {
    await this.issuerRows.nth(index).locator('a.action-icon').filter({ has: this.page.locator('mat-icon:has-text("settings")') }).click();
    await this.waitForReady();
  }

  async clickDeleteIssuer(index: number): Promise<void> {
    await this.issuerRows.nth(index).locator('[data-testid="issuerDeleteAction"]').click();
  }
}
