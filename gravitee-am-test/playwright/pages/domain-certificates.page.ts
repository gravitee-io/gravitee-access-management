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

/** Page object for Domain > Settings > Certificates list + settings dialog. */
export class DomainCertificatesPage extends BasePage {
  async navigateTo(domainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/certificates`);
  }

  /* ------------------------------------------------------------------ */
  /*  Header actions                                                     */
  /* ------------------------------------------------------------------ */

  get addButton(): Locator {
    return this.page.locator('a[mat-raised-button]').filter({ hasText: /add new/i });
  }

  get rotateButton(): Locator {
    return this.page.locator('button').filter({ hasText: /rotate system key/i });
  }

  get settingsButton(): Locator {
    return this.page.locator('button').filter({ hasText: /settings/i });
  }

  /* ------------------------------------------------------------------ */
  /*  Certificate table                                                  */
  /* ------------------------------------------------------------------ */

  get certificateRows(): Locator {
    return this.page.locator('ngx-datatable .datatable-body-row');
  }

  get emptyState(): Locator {
    return this.page.locator('app-emptystate');
  }

  async getCertificateCount(): Promise<number> {
    return this.certificateRows.count();
  }

  /** Find a certificate row by name. */
  certificateRow(name: string | RegExp): Locator {
    return this.certificateRows.filter({ hasText: name });
  }

  /** Check if a row has the "Fallback" badge. */
  fallbackBadge(row: Locator): Locator {
    return row.locator('[data-testid="certificateFallbackBadge"]');
  }

  /** Delete button for a specific row. */
  deleteButton(row: Locator): Locator {
    return row.locator('button').filter({ has: this.page.locator('mat-icon:has-text("delete")') });
  }

  /** Settings link for a specific row. */
  settingsLink(row: Locator): Locator {
    return row.locator('a').filter({ has: this.page.locator('mat-icon:has-text("settings")') });
  }

  /* ------------------------------------------------------------------ */
  /*  Settings dialog                                                    */
  /* ------------------------------------------------------------------ */

  get settingsDialog(): Locator {
    return this.page.locator('mat-dialog-container');
  }

  get fallbackCertificateSelect(): Locator {
    return this.settingsDialog.locator('mat-select');
  }

  async openSettingsDialog(): Promise<void> {
    await this.settingsButton.click();
    await this.settingsDialog.waitFor({ state: 'visible' });
  }

  async selectFallbackCertificate(certName: string | RegExp): Promise<void> {
    await this.fallbackCertificateSelect.click();
    await this.page.locator('mat-option').filter({ hasText: certName }).click();
  }

  async confirmSettingsDialog(): Promise<void> {
    await this.settingsDialog.getByRole('button', { name: /confirm/i }).click();
    await this.settingsDialog.waitFor({ state: 'hidden' });
  }

  async clearSettingsDialog(): Promise<void> {
    await this.settingsDialog.getByRole('button', { name: /clear/i }).click();
  }

  async cancelSettingsDialog(): Promise<void> {
    await this.settingsDialog.getByRole('button', { name: /cancel/i }).click();
    await this.settingsDialog.waitFor({ state: 'hidden' });
  }
}
