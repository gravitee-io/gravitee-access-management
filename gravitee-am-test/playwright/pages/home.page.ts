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

/** Page object for the AM Console home / dashboard page. */
export class HomePage extends BasePage {
  get sidenav(): Locator {
    return this.page.locator('.gio-side-nav').first();
  }

  /** Navigate to the domains list page. */
  async gotoDomainsList(): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains`);
  }

  /** Domains list container. */
  get domainsContent(): Locator {
    return this.page.locator('.domains-content');
  }

  /** Click a sidenav item by label (e.g. 'Applications'). */
  async clickSidenavItem(label: string): Promise<void> {
    const item = this.page
      .locator('.gio-side-nav a')
      .filter({ hasText: new RegExp(label, 'i') })
      .first();
    await item.click();
    await this.waitForReady();
  }

  /** Locator for a domain link by name. */
  domainLink(name: string): Locator {
    return this.page.locator('.domain-information a').filter({ hasText: name }).first();
  }

  get datatable(): Locator {
    return this.page.locator('ngx-datatable');
  }

  /** Row container for a domain in the "All domains" list, by name — filters via the search box first, same as navigateToDomain, to avoid pagination issues when many domains exist. */
  async domainRow(name: string): Promise<Locator> {
    const searchInput = this.page.getByPlaceholder(/search/i).first();
    await expect(searchInput).toBeVisible();
    await searchInput.fill(name);
    return this.datatable.locator('.datatable-body-row').filter({ hasText: name });
  }

  /** Toggle the default-domain star for a row on the "All domains" list page. */
  async toggleDefaultInList(name: string): Promise<void> {
    await this.domainRow(name); // filters the datatable so the row is actually rendered first
    await this.page.getByTestId(`domainDefaultToggle-${name}`).click();
  }

  /** Toggle the pin control for a row on the "All domains" list page. */
  async togglePinInList(name: string): Promise<void> {
    await this.domainRow(name); // filters the datatable so the row is actually rendered first
    await this.page.getByTestId(`domainPinToggle-${name}`).click();
  }

  /** Default-domain star icon for a row on the "All domains" list page — asserts via toHaveText, not a one-shot read, since the icon updates asynchronously after the toggle click. */
  async defaultIconInList(name: string): Promise<Locator> {
    await this.domainRow(name);
    return this.page.getByTestId(`domainDefaultToggle-${name}`).locator('mat-icon');
  }

  /** Pin/bookmark icon for a row on the "All domains" list page — asserts via toHaveText, not a one-shot read, since the icon updates asynchronously after the toggle click. */
  async pinIconInList(name: string): Promise<Locator> {
    await this.domainRow(name);
    return this.page.getByTestId(`domainPinToggle-${name}`).locator('mat-icon');
  }

  /** Navigate to a domain's detail page via the domains list search. */
  async navigateToDomain(domainName: string): Promise<void> {
    await this.gotoDomainsList();

    // Use the search box to filter — avoids pagination issues with many domains
    const searchInput = this.page.getByPlaceholder(/search/i).first();
    await expect(searchInput).toBeVisible();
    await searchInput.fill(domainName);

    const link = this.domainLink(domainName);
    await expect(link).toBeVisible();
    await link.click();
    await this.waitForReady();
  }

  /** Assert the current URL matches a pattern. */
  async expectUrlMatches(pattern: RegExp): Promise<void> {
    await expect(this.page).toHaveURL(pattern);
  }
}
