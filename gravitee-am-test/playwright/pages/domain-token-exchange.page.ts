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

/** Page object for Domain > Settings > OAuth > Token Exchange > Settings. */
export class DomainTokenExchangePage extends BasePage {
  async navigateTo(domainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/token-exchange/settings`);
  }

  /* ------------------------------------------------------------------ */
  /*  Tab navigation                                                     */
  /* ------------------------------------------------------------------ */

  get settingsTab(): Locator {
    return this.page.locator('a[mat-tab-link]').filter({ hasText: /settings/i });
  }

  get trustedIssuersTab(): Locator {
    return this.page.locator('a[mat-tab-link]').filter({ hasText: /trusted issuers/i });
  }

  /* ------------------------------------------------------------------ */
  /*  Enable toggle                                                      */
  /* ------------------------------------------------------------------ */

  get enableToggle(): Locator {
    return this.page.getByRole('switch', { name: /Enable Token Exchange/i });
  }

  async toggleEnable(): Promise<void> {
    await this.enableToggle.click();
    await this.waitForReady();
  }

  /* ------------------------------------------------------------------ */
  /*  Subject / Requested token type selects (inside *ngIf)              */
  /* ------------------------------------------------------------------ */

  get subjectTokenTypesSelect(): Locator {
    return this.page.locator('mat-select[name="allowedSubjectTokenTypes"]');
  }

  get requestedTokenTypesSelect(): Locator {
    return this.page.locator('mat-select[name="allowedRequestedTokenTypes"]');
  }

  /* ------------------------------------------------------------------ */
  /*  Impersonation / Delegation toggles                                 */
  /* ------------------------------------------------------------------ */

  get impersonationToggle(): Locator {
    return this.page.getByRole('switch', { name: /Allow Impersonation/i });
  }

  get delegationToggle(): Locator {
    return this.page.getByRole('switch', { name: /Allow Delegation/i });
  }

  /* ------------------------------------------------------------------ */
  /*  Delegation sub-fields (inside *ngIf="allowDelegation")             */
  /* ------------------------------------------------------------------ */

  get actorTokenTypesSelect(): Locator {
    return this.page.locator('mat-select[name="allowedActorTokenTypes"]');
  }

  get maxDelegationDepthInput(): Locator {
    return this.page.locator('input[name="maxDelegationDepth"]');
  }

  /* ------------------------------------------------------------------ */
  /*  Scope handling select                                              */
  /* ------------------------------------------------------------------ */

  get scopeHandlingSelect(): Locator {
    return this.page.locator('mat-select[name="tokenExchangeScopeHandling"]');
  }

  /** Select a scope handling option from the overlay dropdown. */
  async selectScopeHandling(label: RegExp | string): Promise<void> {
    await this.scopeHandlingSelect.click();
    await this.page.locator('mat-option').filter({ hasText: label }).click();
  }

  /* ------------------------------------------------------------------ */
  /*  Validation + Save                                                  */
  /* ------------------------------------------------------------------ */

  get validationErrors(): Locator {
    return this.page.locator('.validation-error');
  }

  get saveButton(): Locator {
    return this.page.locator('button[type="submit"]').filter({ hasText: /save/i });
  }
}
