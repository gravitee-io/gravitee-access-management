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

/** Page object for Domain > Settings > Multi-factor Authentication (factors list + creation wizard). */
export class DomainFactorsPage extends BasePage {
  async navigateTo(domainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/factors`);
  }

  /* ------------------------------------------------------------------ */
  /*  Factor list                                                        */
  /* ------------------------------------------------------------------ */

  get factorRows(): Locator {
    return this.page.locator('ngx-datatable .datatable-body-row');
  }

  get emptyState(): Locator {
    return this.page.locator('app-emptystate');
  }

  async getFactorCount(): Promise<number> {
    return this.factorRows.count();
  }

  factorRow(name: string | RegExp): Locator {
    return this.factorRows.filter({ hasText: name });
  }

  /* ------------------------------------------------------------------ */
  /*  Add button (FAB)                                                   */
  /* ------------------------------------------------------------------ */

  get addButton(): Locator {
    // factors.component.html uses <a mat-fab> (link, not button)
    return this.page.locator('a[mat-fab]');
  }

  /* ------------------------------------------------------------------ */
  /*  Creation wizard (stepper)                                          */
  /* ------------------------------------------------------------------ */

  get stepper(): Locator {
    return this.page.locator('mat-horizontal-stepper');
  }

  get nextButton(): Locator {
    return this.page.getByRole('button', { name: /next/i });
  }

  get createButton(): Locator {
    return this.page.getByRole('button', { name: /create/i });
  }

  /** Name input field on the factor creation/edit form. */
  get nameInput(): Locator {
    return this.page.getByTestId('factorNameInput');
  }

  /** Select a factor type in step 1 of the creation wizard by plugin ID. */
  async selectFactorType(pluginId: string): Promise<void> {
    // Each factor card has data-testid="factorType-{pluginId}" (added to step1.component.html)
    // Radio is inside *ngIf="factor.deployed" — must be visible to prove plugin is deployed
    const card = this.page.getByTestId(`factorType-${pluginId}`);
    const radio = card.getByRole('radio');
    await radio.click();
  }

  /* ------------------------------------------------------------------ */
  /*  Factor detail page                                                 */
  /* ------------------------------------------------------------------ */

  /** Navigate to a specific factor's detail page. */
  async navigateToFactor(domainId: string, factorId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/factors/${factorId}`);
  }

  get saveButton(): Locator {
    return this.page.getByRole('button', { name: /^save$/i });
  }

  /** Delete button on the factor detail page (bottom danger zone — class from factor.component.html). */
  get factorDeleteButton(): Locator {
    return this.page.locator('.gv-page-delete-zone').getByRole('button', { name: /delete/i });
  }

  /** Factor name heading on the detail/creation page (h1 inside the page container). */
  get factorHeading(): Locator {
    return this.page.getByRole('heading', { level: 1 });
  }
}
