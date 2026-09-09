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
 * Page object for Domain > Settings > Audit Log > Settings, where reporters are listed and edited.
 *
 * The plugin configuration is rendered by @ajsf from the reporter's own schema, so those fields are
 * addressed by schema property name. The attribute mapping section is a hand-written form and is
 * addressed by test id.
 */
export class DomainReporterPage extends BasePage {
  async navigateToList(domainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/audits/settings`);
  }

  async navigateToCreate(domainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/audits/settings/new`);
  }

  async navigateToDetail(domainId: string, reporterId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/audits/settings/${reporterId}`);
  }

  /** Organization-level reporters live outside the environment tree. */
  async navigateToOrgDetail(reporterId: string): Promise<void> {
    await this.navigate(`/settings/audits/settings/${reporterId}`);
  }

  /* ------------------------------------------------------------------ */
  /*  Reporter detail                                                    */
  /* ------------------------------------------------------------------ */

  get nameInput(): Locator {
    return this.page.getByTestId('reporterNameInput');
  }

  get typeSelect(): Locator {
    return this.page.getByTestId('reporterTypeSelect');
  }

  get saveButton(): Locator {
    return this.page.getByTestId('reporterSaveButton');
  }

  /** A configuration field, addressed by its schema property name. */
  configurationField(property: string): Locator {
    return this.page.locator(`json-schema-form input[name="${property}"]`);
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }

  /* ------------------------------------------------------------------ */
  /*  Attribute mapping                                                  */
  /* ------------------------------------------------------------------ */

  get attributeMappingsSection(): Locator {
    return this.page.getByTestId('attributeMappingsSection');
  }

  get mappingRows(): Locator {
    return this.page.getByTestId('attributeMappingRow');
  }

  mappingNameInput(index: number): Locator {
    return this.mappingRows.nth(index).getByTestId('attributeMappingNameInput');
  }

  mappingExpressionInput(index: number): Locator {
    return this.mappingRows.nth(index).getByTestId('attributeMappingExpressionInput');
  }

  /** Append a mapping and fill the row it created. */
  async addMapping(exportedName: string, expression: string): Promise<void> {
    const index = await this.mappingRows.count();
    await this.page.getByTestId('addAttributeMappingButton').click();
    await this.mappingNameInput(index).fill(exportedName);
    await this.mappingExpressionInput(index).fill(expression);
    await this.mappingExpressionInput(index).blur();
  }

  async removeMapping(index: number): Promise<void> {
    await this.mappingRows.nth(index).getByTestId('removeAttributeMappingButton').click();
  }

  mappingFieldError(index: number): Locator {
    return this.mappingRows.nth(index).locator('mat-error');
  }

  get validationErrors(): Locator {
    return this.page.getByTestId('attributeMappingValidationError');
  }

  /** The event types the mappings are limited to, as listed under the picker. */
  get selectedEventTypes(): Locator {
    return this.page.getByTestId('attributeMappingEventTypesSelectedItem');
  }

  get eventTypesEmptyMessage(): Locator {
    return this.page.getByTestId('attributeMappingEventTypesEmptyMessage');
  }

  /** Open the event type picker, tick each name, and close the overlay. */
  async selectEventTypes(eventTypes: string[]): Promise<void> {
    await this.page.getByTestId('attributeMappingEventTypesSelect').click();
    // The panel renders in the CDK overlay, outside the field.
    const panel = this.page.locator('.mat-mdc-select-panel');
    await panel.waitFor({ state: 'visible' });
    for (const eventType of eventTypes) {
      await this.page.getByTestId('attributeMappingEventTypesSearchInput').fill(eventType);
      await panel
        .locator('mat-option')
        .filter({ hasText: new RegExp(`^\\s*${eventType}\\s*$`) })
        .click();
    }
    await this.page.keyboard.press('Escape');
    await panel.waitFor({ state: 'hidden' });
  }

  /* ------------------------------------------------------------------ */
  /*  Kafka's own event filter, which is a separate control              */
  /* ------------------------------------------------------------------ */

  get reportedEventTypesSection(): Locator {
    return this.page.locator('json-schema-form material-multiselect-widget');
  }
}
