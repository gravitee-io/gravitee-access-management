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

/**
 * Page object for Domain > Settings > Resources.
 *
 * The configuration form is rendered by @ajsf from the plugin's schema, so its
 * fields are addressed by the schema property name rather than by a test id.
 * Fields whose schema condition is false are absent from the DOM, not hidden.
 */
export class DomainResourcesPage extends BasePage {
  async navigateToList(domainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/resources`);
  }

  async navigateToDetail(domainId: string, resourceId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/resources/${resourceId}`);
    await this.configurationForm.waitFor({ state: 'visible' });
  }

  async navigateToCreate(domainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/resources/new`);
  }

  /* ------------------------------------------------------------------ */
  /*  Resource list                                                      */
  /* ------------------------------------------------------------------ */

  get resourceRows(): Locator {
    return this.page.locator('ngx-datatable .datatable-body-row');
  }

  resourceRow(name: string | RegExp): Locator {
    return this.resourceRows.filter({ hasText: name });
  }

  /** Settings link for a specific row, which opens the resource for editing. */
  settingsLink(row: Locator): Locator {
    return row.locator('a').filter({ has: this.page.locator('mat-icon:has-text("settings")') });
  }

  /* ------------------------------------------------------------------ */
  /*  Creation wizard                                                    */
  /* ------------------------------------------------------------------ */

  get typeCards(): Locator {
    return this.page.locator('mat-card.plugin-type-card');
  }

  async selectResourceType(name: string | RegExp): Promise<void> {
    await this.typeCards.first().waitFor({ state: 'visible' });
    await this.typeCards.filter({ hasText: name }).first().click();
  }

  async clickNext(): Promise<void> {
    await this.page.locator('button').filter({ hasText: /next/i }).first().click();
    await this.configurationForm.waitFor({ state: 'visible' });
  }

  /** Submit the creation wizard and wait for the app to leave the wizard route. */
  async clickCreate(): Promise<void> {
    await this.page
      .locator('button')
      .filter({ hasText: /create/i })
      .first()
      .click();
    await this.page.waitForURL((url) => !url.pathname.endsWith('/new'));
  }

  /* ------------------------------------------------------------------ */
  /*  Schema-driven configuration form                                   */
  /* ------------------------------------------------------------------ */

  get configurationForm(): Locator {
    return this.page.locator('json-schema-form');
  }

  /** A text or number input, addressed by its schema property name. */
  field(property: string): Locator {
    return this.configurationForm.locator(`input[name="${property}"]`);
  }

  /** A mat-select, addressed by its schema property name. */
  select(property: string): Locator {
    return this.configurationForm.locator(`mat-select[name="${property}"]`);
  }

  /** The mat-checkbox wrapping a boolean property's input. */
  checkbox(property: string): Locator {
    return this.configurationForm.locator('mat-checkbox').filter({ has: this.page.locator(`input[name="${property}"]`) });
  }

  async setField(property: string, value: string): Promise<void> {
    const input = this.field(property);
    await input.fill(value);
    await input.blur();
  }

  async toggleCheckbox(property: string): Promise<void> {
    await this.checkbox(property).click();
  }

  async chooseOption(property: string, option: string | RegExp): Promise<void> {
    await this.select(property).click();
    await this.page.locator('mat-option').filter({ hasText: option }).first().click();
  }

  /** Fill the SMTP fields that every scenario needs. */
  async fillSmtpBasics(host: string, from: string): Promise<void> {
    await this.setField('host', host);
    await this.setField('from', from);
  }

  /* ------------------------------------------------------------------ */
  /*  Resource detail                                                    */
  /* ------------------------------------------------------------------ */

  get nameInput(): Locator {
    return this.page.locator('input[name="name"]');
  }

  get saveButton(): Locator {
    return this.page.locator('button').filter({ hasText: /save/i }).first();
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }
}
