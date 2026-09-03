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

/** Page object for Domain > Settings > Providers, both the creation wizard and one provider's settings. */
export class IdentityProviderSettingsPage extends BasePage {
  async navigateToSettings(domainId: string, providerId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/providers/${providerId}/settings`);
  }

  async navigateToCreation(domainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/providers/new`);
  }

  /** Picks a provider type in step 1 of the wizard and moves to the configuration step. */
  async chooseType(label: string): Promise<void> {
    await this.page.getByText(label, { exact: true }).click();
    await this.page.locator('button:has-text("Next")').click();
  }

  get databaseField(): Locator {
    return this.page.locator('input[name="database"]');
  }

  get usersCollectionField(): Locator {
    return this.page.locator('input[name="usersCollection"]');
  }

  get systemClusterToggle(): Locator {
    return this.page.locator('mat-checkbox').filter({ hasText: 'Use System Cluster' }).locator('input[type=checkbox]');
  }

  get descriptionPanel(): Locator {
    return this.page.locator('.gv-page-description-content');
  }

  get saveButton(): Locator {
    return this.page.locator('button:has-text("SAVE")');
  }

  /** A hint does not sit inside its field's form-field element, so match it on its own text. */
  hint(startsWith: string): Locator {
    return this.page.locator('.mat-mdc-form-field-hint').filter({ hasText: startsWith });
  }
}
