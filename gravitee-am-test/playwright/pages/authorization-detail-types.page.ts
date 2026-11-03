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

/** Page object for Domain > Settings > OAuth 2.0 > RAR: the list, the create form and the edit screen. */
export class AuthorizationDetailTypesPage extends BasePage {
  async navigateTo(domainId: string): Promise<void> {
    await this.navigate(this.listPath(domainId));
  }

  async navigateToType(domainId: string, typeId: string): Promise<void> {
    await this.navigate(`${this.listPath(domainId)}/${typeId}`);
  }

  listPath(domainId: string): string {
    return `/environments/${this.envHrid}/domains/${domainId}/settings/authorization-detail-types`;
  }

  // ----- list -----

  get heading(): Locator {
    return this.page.getByRole('heading', { level: 1 });
  }

  get emptyState(): Locator {
    return this.page.getByText('Authorization detail types will appear here');
  }

  get createButton(): Locator {
    return this.page.getByRole('link', { name: 'Create' });
  }

  get rows(): Locator {
    return this.page.locator('ngx-datatable datatable-body-row');
  }

  get pageCount(): Locator {
    return this.page.locator('ngx-datatable .page-count');
  }

  /** The type column is the second cell of each row. */
  get rowTypes(): Locator {
    return this.rows.locator('datatable-body-cell:nth-child(2)');
  }

  rowByType(type: string): Locator {
    return this.rows.filter({ has: this.page.getByText(type, { exact: true }) });
  }

  discoveryToggle(type: string): Locator {
    return this.rowByType(type).getByRole('switch');
  }

  editAction(type: string): Locator {
    return this.rowByType(type).getByRole('link', { name: 'Edit' });
  }

  deleteAction(type: string): Locator {
    return this.rowByType(type).getByRole('button', { name: 'Delete' });
  }

  // The pager's anchors have no href, so they carry no link role: match their aria-label instead.
  // A numbered page labels its <li>, the arrows label their <a>.
  numberedLink(pageNumber: number): Locator {
    return this.page.locator(`datatable-pager [aria-label="page ${pageNumber}"]`);
  }

  get nextLink(): Locator {
    return this.page.locator('datatable-pager a[aria-label="go to next page"]');
  }

  get previousLink(): Locator {
    return this.page.locator('datatable-pager a[aria-label="go to previous page"]');
  }

  // ----- create and edit forms -----

  get typeInput(): Locator {
    return this.page.locator('input[name="type"]');
  }

  get nameInput(): Locator {
    return this.page.locator('input[name="name"]');
  }

  get descriptionInput(): Locator {
    return this.page.locator('input[name="description"]');
  }

  get durationInput(): Locator {
    return this.page.locator('input[name="expiresIn"]');
  }

  get unitSelect(): Locator {
    return this.page.locator('mat-select[name="unitTime"]');
  }

  get schemaEditor(): Locator {
    return this.page.locator('ngx-codemirror .CodeMirror');
  }

  get formatButton(): Locator {
    return this.page.getByRole('button').filter({ hasText: /Format/ });
  }

  get clearExpiryButton(): Locator {
    return this.page.getByRole('button', { name: 'clear' });
  }

  get currentPeriod(): Locator {
    return this.page
      .locator('span')
      .filter({ hasText: /^Current period:/ })
      .locator('xpath=..');
  }

  get createSubmit(): Locator {
    return this.page.getByRole('button', { name: 'CREATE' });
  }

  get saveButton(): Locator {
    return this.page.getByRole('button', { name: 'SAVE' });
  }

  get deleteButton(): Locator {
    return this.page.getByRole('button', { name: 'DELETE' });
  }

  async selectUnit(unit: string): Promise<void> {
    await this.unitSelect.click();
    // mat-select options render in a page-level CDK overlay (GUIDELINES §18).
    await this.page.locator('mat-option').filter({ hasText: unit }).click();
  }
}
