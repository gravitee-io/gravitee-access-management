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
import { Page, expect } from '@playwright/test';

/** Base page object for AM Console (Angular SPA). */
export abstract class BasePage {
  constructor(protected readonly page: Page) {}

  /** Environment hrid used in Angular route paths (lowercase, e.g. 'default'). */
  protected get envHrid(): string {
    return process.env.AM_DEF_ENV_HRID || 'default';
  }

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
    );
    // Wait for spinners to clear — resolves immediately if none are present
    await this.page.locator('mat-spinner, mat-progress-bar').first().waitFor({ state: 'hidden' });
  }

  /** Assert a snackbar appears with expected text, then wait for it to dismiss. */
  async expectSnackbar(expectedText: string | RegExp): Promise<void> {
    const snackbar = this.page.locator('simple-snack-bar').last();
    await expect(snackbar).toBeVisible();
    await expect(snackbar).toContainText(expectedText);
    await snackbar.waitFor({ state: 'hidden' });
  }

  /** Click the primary save/submit button. */
  async clickSave(): Promise<void> {
    await this.page.locator('button[type="submit"]').filter({ hasText: /save/i }).first().click();
  }

  /** Wait for significant page content to appear (positive anchor for negative assertions). */
  async waitForPageContent(): Promise<void> {
    await this.page.locator('h1, h2, ngx-datatable, .gv-page-container').first().waitFor({ state: 'visible' });
  }

  async confirmDialog(): Promise<void> {
    const dialog = this.page.locator('mat-dialog-container');
    await dialog.waitFor({ state: 'visible' });
    await dialog.getByRole('button', { name: /ok|confirm|yes|delete/i }).click();
    await dialog.waitFor({ state: 'hidden' });
  }
}
