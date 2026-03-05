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

/** Page object for Application > Settings > General. */
export class ApplicationGeneralSettingsPage extends BasePage {
  /** Navigate directly to an application's general settings page. */
  async navigateTo(domainId: string, appId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/applications/${appId}/settings/general`);
  }

  get nameInput(): Locator {
    return this.page.locator('input[name="name"]');
  }

  get agentCardUrlInput(): Locator {
    return this.page.locator('input[name="agentCardUrl"]');
  }

  /** Update the application name. */
  async fillName(name: string): Promise<void> {
    await this.nameInput.clear();
    await this.nameInput.fill(name);
  }

  /** Update the agent card URL. */
  async fillAgentCardUrl(url: string): Promise<void> {
    await this.agentCardUrlInput.clear();
    await this.agentCardUrlInput.fill(url);
  }

  /** Click the DELETE button in the danger zone. */
  async clickDelete(): Promise<void> {
    await this.page.getByRole('button', { name: /delete/i }).click();
  }
}
