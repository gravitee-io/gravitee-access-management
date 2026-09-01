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

export type TrustDomainUsage = 'SPIFFE' | 'ISSUER';

/** Page object for the merged Trusted Domain create/edit form. */
export class TrustedDomainDetailPage extends BasePage {
  async navigateToNew(domainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/trust-domains/domains/new`);
  }

  async navigateToEdit(domainId: string, trustDomainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/trust-domains/domains/${trustDomainId}`);
  }

  /* ------------------------------------------------------------------ */
  /*  Usage — a trusted domain declares one or both                      */
  /* ------------------------------------------------------------------ */

  usageChoice(usage: TrustDomainUsage): Locator {
    return this.page.locator(`[data-testid="usageChoice-${usage}"]`);
  }

  async setUsage(usage: TrustDomainUsage, enabled: boolean): Promise<void> {
    const checkbox = this.usageChoice(usage).locator('input[type="checkbox"]');
    if ((await checkbox.isChecked()) !== enabled) {
      await this.usageChoice(usage).locator('label').click();
      await this.waitForReady();
    }
  }

  async isUsageSelected(usage: TrustDomainUsage): Promise<boolean> {
    return this.usageChoice(usage).locator('input[type="checkbox"]').isChecked();
  }

  get usageBadge(): Locator {
    return this.page.locator('[data-testid="trustDomainUsage"]');
  }

  /* ------------------------------------------------------------------ */
  /*  Identity                                                           */
  /* ------------------------------------------------------------------ */

  get nameInput(): Locator {
    return this.page.locator('[data-testid="nameInput"]');
  }

  get issuerUrlInput(): Locator {
    return this.page.locator('[data-testid="issuerUrlInput"]');
  }

  get spiffeTrustDomainInput(): Locator {
    return this.page.locator('[data-testid="spiffeTrustDomainInput"]');
  }

  get title(): Locator {
    return this.page.locator('[data-testid="trustDomainTitle"]');
  }

  /* ------------------------------------------------------------------ */
  /*  Key material — one control shared by both usages                   */
  /* ------------------------------------------------------------------ */

  get keySourceSelect(): Locator {
    return this.page.locator('[data-testid="keySourceSelect"]');
  }

  get jwksUrlInput(): Locator {
    return this.page.locator('[data-testid="jwksUrlInput"]');
  }

  get jwkSetTextarea(): Locator {
    return this.page.locator('[data-testid="jwkSetTextarea"]');
  }

  get pemCertTextarea(): Locator {
    return this.page.locator('[data-testid="pemCertTextarea"]');
  }

  async selectKeySource(label: RegExp | string): Promise<void> {
    await this.keySourceSelect.click();
    await this.page.locator('mat-option').filter({ hasText: label }).click();
    await this.waitForReady();
  }

  /* ------------------------------------------------------------------ */
  /*  Scope mappings (trusted-issuer usage only)                         */
  /* ------------------------------------------------------------------ */

  get externalScopeInput(): Locator {
    return this.page.locator('[data-testid="externalScopeInput"]');
  }

  get domainScopeInput(): Locator {
    return this.page.locator('[data-testid="domainScopeAutocomplete"]');
  }

  get addScopeMappingButton(): Locator {
    return this.page.locator('[data-testid="addScopeMappingButton"]');
  }

  get scopeMappingRows(): Locator {
    return this.page.locator('[data-testid="scopeMappingsTable"] .datatable-body-row');
  }

  async addScopeMapping(externalScope: string, domainScope: string): Promise<void> {
    await this.externalScopeInput.fill(externalScope);

    await expect(this.domainScopeInput).toBeVisible();
    await this.domainScopeInput.fill(domainScope);
    await this.page.locator('mat-option').filter({ hasText: domainScope }).first().click();

    await this.addScopeMappingButton.click();
    await this.waitForReady();
  }

  /* ------------------------------------------------------------------ */
  /*  User binding (trusted-issuer usage only)                           */
  /* ------------------------------------------------------------------ */

  get userBindingToggle(): Locator {
    return this.page.getByRole('switch', { name: /Enable User Binding/i });
  }

  get userAttributeInput(): Locator {
    return this.page.locator('[data-testid="userBindingAttributeInput"]');
  }

  get claimExpressionInput(): Locator {
    return this.page.locator('[data-testid="userBindingExpressionInput"]');
  }

  get addUserBindingButton(): Locator {
    return this.page.locator('[data-testid="addUserBindingButton"]');
  }

  get userBindingRows(): Locator {
    return this.page.locator('[data-testid="userBindingTable"] .datatable-body-row');
  }

  async addUserBinding(attribute: string, expression: string): Promise<void> {
    await this.userAttributeInput.fill(attribute);
    await this.claimExpressionInput.fill(expression);
    await this.addUserBindingButton.click();
    await this.waitForReady();
  }

  /* ------------------------------------------------------------------ */
  /*  SPIFFE-only settings                                               */
  /* ------------------------------------------------------------------ */

  get refreshIntervalInput(): Locator {
    return this.page.locator('[data-testid="refreshIntervalInput"]');
  }

  get allowedAlgorithmsInput(): Locator {
    return this.page.locator('input[name="algorithmInput"]');
  }

  /* ------------------------------------------------------------------ */
  /*  Save / Delete                                                      */
  /* ------------------------------------------------------------------ */

  get saveButton(): Locator {
    return this.page.locator('[data-testid="saveButton"]');
  }

  get deleteButton(): Locator {
    return this.page.locator('[data-testid="deleteButton"]');
  }

  get validationErrors(): Locator {
    return this.page.locator('[data-testid="validationError"]');
  }
}
