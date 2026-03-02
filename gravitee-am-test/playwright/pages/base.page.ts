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
import { Page, Locator, expect } from '@playwright/test';

/** Base page object for AM Console (Angular SPA). */
export abstract class BasePage {
  constructor(protected readonly page: Page) {}

  /** Navigate to a path relative to baseURL. */
  async navigate(path: string): Promise<void> {
    await this.page.goto(path);
    await this.waitForReady();
  }

  /** Wait for Angular to render content and spinners to clear. */
  async waitForReady(): Promise<void> {
    await this.page.waitForLoadState('domcontentloaded');
    // Wait for Angular stability OR meaningful content. Pages with
    // continuous polling (dashboards, charts) may never fully stabilize.
    await this.page.waitForFunction(
      () => {
        const ng = (window as any).getAllAngularTestabilities?.();
        if (ng && ng.length > 0 && ng[0].isStable()) {
          return true;
        }
        const root = document.querySelector('app-root');
        if (!root) return false;
        return root.querySelector('h1, h2, ngx-datatable, .gv-page-container') !== null;
      },
      { timeout: 15_000 },
    );
    const spinner = this.page.locator('mat-spinner, mat-progress-bar');
    if (await spinner.isVisible()) {
      await spinner.waitFor({ state: 'hidden', timeout: 30_000 });
    }
  }

  get pageTitle(): Locator {
    return this.page.locator('h1, h2, .page-title, .gv-page-title').first();
  }

  async currentPath(): Promise<string> {
    return new URL(this.page.url()).pathname;
  }

  /** Assert a snackbar appears with expected text. */
  async expectSnackbar(expectedText: string): Promise<void> {
    const snackbar = this.page.locator('simple-snack-bar, mat-snack-bar-container');
    await expect(snackbar).toBeVisible({ timeout: 10_000 });
    await expect(snackbar).toContainText(expectedText);
  }

  async clickNavItem(text: string): Promise<void> {
    await this.page.locator('.gio-side-nav a').filter({ hasText: text }).click();
    await this.waitForReady();
  }

  async clickButton(text: string): Promise<void> {
    await this.page.getByRole('button', { name: text }).click();
  }

  async fillInput(label: string, value: string): Promise<void> {
    await this.page.getByLabel(label).fill(value);
  }

  async selectOption(selectLabel: string, optionText: string): Promise<void> {
    await this.page.locator('mat-select').filter({ hasText: selectLabel }).click();
    await this.page.locator('mat-option').filter({ hasText: optionText }).click();
  }

  async confirmDialog(): Promise<void> {
    const dialog = this.page.locator('mat-dialog-container');
    await dialog.waitFor({ state: 'visible' });
    await dialog.getByRole('button', { name: /ok|confirm|yes|delete/i }).click();
    await dialog.waitFor({ state: 'hidden' });
  }
}
