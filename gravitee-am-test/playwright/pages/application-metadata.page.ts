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
import { Locator, expect } from '@playwright/test';
import { BasePage } from './base.page';

/** Page object for Application > Settings > Metadata (ngx-datatable based). */
export class ApplicationMetadataPage extends BasePage {
  async navigateTo(domainId: string, appId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/applications/${appId}/settings/metadata`);
  }

  get datatable(): Locator {
    return this.page.locator('ngx-datatable');
  }

  /** Add a metadata key-value pair. */
  async addMetadata(name: string, value: string): Promise<void> {
    await this.page.locator('input[name="Name"]').fill(name);
    await this.page.locator('input[name="metadataValue"]').fill(value);
    await this.page.getByRole('button', { name: /add/i }).click();
  }

  /** Delete a metadata entry by name. */
  async deleteMetadata(name: string): Promise<void> {
    const row = this.datatable.locator('.datatable-body-row').filter({ hasText: name });
    await row.locator('button[mat-icon-button]').click();
  }

  /** Assert a metadata row with given name and value exists. */
  async expectMetadataRow(name: string, value: string): Promise<void> {
    const row = this.datatable.locator('.datatable-body-row').filter({ hasText: name });
    await expect(row.first()).toBeVisible();
    await expect(row.first()).toContainText(value);
  }

  /** Assert a metadata row with given name does NOT exist. */
  async expectNoMetadataRow(name: string): Promise<void> {
    const row = this.datatable.locator('.datatable-body-row').filter({ hasText: name });
    await expect(row).toHaveCount(0);
  }
}
