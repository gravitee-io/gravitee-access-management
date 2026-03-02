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
import { Page, Locator } from '@playwright/test';

/** Reusable locator helpers for Angular Material components. */

export function byTestId(page: Page, testId: string): Locator {
  return page.locator(`[data-testid="${testId}"]`);
}

export function matTable(page: Page, testId?: string): Locator {
  return testId ? byTestId(page, testId) : page.locator('mat-table, table[mat-table]');
}

export function matCardByTitle(page: Page, title: string): Locator {
  return page.locator('mat-card').filter({ hasText: title });
}

export function matButton(page: Page, text: string): Locator {
  return page.getByRole('button', { name: text });
}

export function matInput(page: Page, label: string): Locator {
  return page.getByLabel(label);
}

export function matSelect(page: Page, label: string): Locator {
  return page.locator('mat-select').filter({ hasText: label });
}

export function matOption(page: Page, text: string): Locator {
  return page.locator('mat-option').filter({ hasText: text });
}

export function navItem(page: Page, text: string): Locator {
  return page.locator('mat-nav-list a, .gv-nav-link, a[mat-list-item]').filter({ hasText: text });
}
