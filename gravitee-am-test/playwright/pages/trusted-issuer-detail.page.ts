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

/** Page object for Trusted Issuer create/edit form. */
export class TrustedIssuerDetailPage extends BasePage {
  async navigateToNew(domainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/token-exchange/trusted-issuers/new`);
  }

  async navigateToEdit(domainId: string, issuerIndex: number): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/token-exchange/trusted-issuers/${issuerIndex}`);
  }

  /* ------------------------------------------------------------------ */
  /*  Issuer configuration                                               */
  /* ------------------------------------------------------------------ */

  get issuerUrlInput(): Locator {
    return this.page.locator('input[name="issuer"]');
  }

  get keyMethodSelect(): Locator {
    return this.page.locator('mat-select[name="keyResolutionMethod"]');
  }

  /** Visible only when key method = JWKS URL. */
  get jwksUriInput(): Locator {
    return this.page.locator('input[name="jwksUri"]');
  }

  /** Visible only when key method = PEM. */
  get pemCertTextarea(): Locator {
    return this.page.locator('textarea[name="certificate"]');
  }

  async selectKeyMethod(label: RegExp | string): Promise<void> {
    await this.keyMethodSelect.click();
    await this.page.locator('mat-option').filter({ hasText: label }).click();
    await this.waitForReady();
  }

  /* ------------------------------------------------------------------ */
  /*  Scope mappings                                                     */
  /* ------------------------------------------------------------------ */

  get externalScopeInput(): Locator {
    return this.page.locator('input[name="newExtScope"]');
  }

  get domainScopeInput(): Locator {
    // Two variants: autocomplete or plain text input depending on whether domain scopes exist
    return this.page.locator('input[name="newDomScope"], input[name="newDomScopeText"]').first();
  }

  get addScopeMappingButton(): Locator {
    return this.page.locator('button').filter({ hasText: /ADD/i }).first();
  }

  get scopeMappingRows(): Locator {
    return this.page.locator('ngx-datatable').first().locator('.datatable-body-row');
  }

  async addScopeMapping(externalScope: string, domainScope: string): Promise<void> {
    await this.externalScopeInput.fill(externalScope);

    // Domain scope uses mat-autocomplete when domain scopes exist, plain text otherwise.
    // This is UI variant detection (not a test assertion), so the conditional is intentional.
    const autocompleteInput = this.page.locator('input[name="newDomScope"]');
    const textInput = this.page.locator('input[name="newDomScopeText"]');

    // Wait for either input to be rendered before checking which variant is active
    await this.page.locator('input[name="newDomScope"], input[name="newDomScopeText"]').first().waitFor({ state: 'visible' });

    if (await autocompleteInput.isVisible().catch(() => false)) {
      await autocompleteInput.fill(domainScope);
      // Wait for and select the autocomplete option
      const autocompleteOption = this.page.locator('mat-option').filter({ hasText: domainScope });
      await autocompleteOption.first().click();
    } else {
      await textInput.fill(domainScope);
    }

    await this.addScopeMappingButton.click();
    await this.waitForReady();
  }

  /* ------------------------------------------------------------------ */
  /*  User binding                                                       */
  /* ------------------------------------------------------------------ */

  get userBindingToggle(): Locator {
    return this.page.getByRole('switch', { name: /Enable User Binding/i });
  }

  get userAttributeInput(): Locator {
    return this.page.locator('input[name="newUbAttr"]');
  }

  get claimExpressionInput(): Locator {
    return this.page.locator('input[name="newUbClaim"]');
  }

  get addUserBindingButton(): Locator {
    // Second ADD button — scoped to the user binding section via nth(1) since
    // addScopeMappingButton takes first(). Using nth() over last() to be explicit.
    return this.page.locator('button').filter({ hasText: /ADD/i }).nth(1);
  }

  get userBindingRows(): Locator {
    return this.page.locator('ngx-datatable').last().locator('.datatable-body-row');
  }

  async addUserBinding(attribute: string, expression: string): Promise<void> {
    await this.userAttributeInput.fill(attribute);
    await this.claimExpressionInput.fill(expression);
    await this.addUserBindingButton.click();
    await this.waitForReady();
  }

  /* ------------------------------------------------------------------ */
  /*  Save / Delete                                                      */
  /* ------------------------------------------------------------------ */

  get saveButton(): Locator {
    return this.page.locator('button[type="submit"]').filter({ hasText: /save/i });
  }

  get deleteButton(): Locator {
    return this.page.locator('button[color="warn"]').filter({ hasText: /delete/i });
  }

  get validationErrors(): Locator {
    return this.page.locator('.validation-error');
  }
}
