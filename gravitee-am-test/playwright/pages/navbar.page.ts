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

type PickerSection = 'Current' | 'Default' | 'Pinned';

/** Page object for the navbar domain picker (opens from the current-domain chip). */
export class NavbarPage extends BasePage {
  /** The current-domain chip in the top bar that triggers the picker. */
  get trigger(): Locator {
    return this.page.getByTestId('domain-picker-trigger');
  }

  /** Default-domain star shown inside the chip when the current domain is the default. */
  get chipDefaultStar(): Locator {
    return this.trigger.locator('.currentDomain__default');
  }

  get search(): Locator {
    return this.page.getByTestId('domain-picker-search');
  }

  /** The picker overlay — scoped by its search box since it shares a panel class with the account menu. */
  get menu(): Locator {
    return this.page.locator('.mat-mdc-menu-panel', { has: this.search });
  }

  /** Open the picker from the chip and wait for it to render. */
  async open(): Promise<void> {
    await this.trigger.click();
    await expect(this.search).toBeVisible();
  }

  sectionLabel(name: PickerSection): Locator {
    return this.menu.locator('.domains_list__label', { hasText: new RegExp(`^${name}$`, 'i') });
  }

  /** The "New" domain link in the picker footer. */
  get newDomainLink(): Locator {
    return this.menu.locator('.userAccountFooter__link', { hasText: /new/i });
  }

  /** The "All domains" link in the picker footer. */
  get allDomainsLink(): Locator {
    return this.menu.locator('.userAccountFooter__link', { hasText: /all domains/i });
  }

  row(name: string): Locator {
    return this.menu.locator('.domains_list__item', {
      has: this.page.locator('.domains_list__item__name', { hasText: name }),
    });
  }

  async searchFor(term: string): Promise<void> {
    await this.search.fill(term);
  }

  /** Toggle the pin control on a row (controls reveal on hover). */
  async pin(name: string): Promise<void> {
    const row = this.row(name);
    await row.hover();
    await row
      .getByRole('button')
      .filter({ has: this.page.locator('mat-icon', { hasText: /bookmark/ }) })
      .click();
  }

  /** Toggle the default control on a row (controls reveal on hover). */
  async setDefault(name: string): Promise<void> {
    const row = this.row(name);
    await row.hover();
    await row
      .getByRole('button')
      .filter({ has: this.page.locator('mat-icon', { hasText: /^star/ }) })
      .click();
  }
}
