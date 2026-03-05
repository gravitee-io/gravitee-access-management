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

/** Page object for the application creation wizard (mat-horizontal-stepper). */
export class ApplicationCreationPage extends BasePage {
  get stepper(): Locator {
    return this.page.locator('mat-horizontal-stepper');
  }

  /** Open the creation wizard by clicking the FAB/add button on the applications list. */
  async openWizard(): Promise<void> {
    await this.page.locator('a[mat-fab], button[mat-fab]').first().click();
    await this.stepper.waitFor({ state: 'visible' });
  }

  /** Locator for an application type card by its display name. */
  appTypeCard(name: string): Locator {
    return this.page.locator('mat-card[appearance="outlined"]').filter({ hasText: name }).first();
  }

  /** Select an application type in step 1 by clicking its card. */
  async selectAppType(name: string): Promise<void> {
    await this.appTypeCard(name).click();
  }

  /** Click the Next button to advance the stepper. */
  async clickNext(): Promise<void> {
    await this.page.locator('button[matStepperNext]').click();
  }

  /** Fill the application name input in step 2. */
  async fillAppName(name: string): Promise<void> {
    await this.page.locator('input[name="name"]').fill(name);
  }

  /** Fill the redirect URI input in step 2. */
  async fillRedirectUri(uri: string): Promise<void> {
    await this.page.getByLabel('Redirect URI').fill(uri);
  }

  /** Fill the agent card URL input (visible only for AGENT type in step 2). */
  async fillAgentCardUrl(url: string): Promise<void> {
    await this.page.locator('input[name="agentCardUrl"]').fill(url);
  }

  get createButton(): Locator {
    return this.page.getByRole('button', { name: /create/i });
  }

  get agentCardUrlInput(): Locator {
    return this.page.locator('input[name="agentCardUrl"]');
  }

  /** Click the Create button to submit the wizard. */
  async clickCreate(): Promise<void> {
    // The create button is the primary raised button that is NOT the stepper-next button
    await this.page.getByRole('button', { name: /create/i }).click();
  }

}
