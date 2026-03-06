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

/** Page object for Application > Settings > OAuth 2.0. */
export class Oauth2SettingsPage extends BasePage {
  /** Navigate directly to an application's OAuth2 settings page. */
  async navigateTo(domainId: string, appId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/applications/${appId}/settings/oauth2`);
  }

  /** The grant flows section containing grant type checkboxes. */
  get grantFlowsSection(): Locator {
    return this.page.getByText('Grant flows', { exact: false }).first();
  }

  /** Locator for a grant type checkbox by label text. */
  grantTypeCheckbox(label: RegExp | string): Locator {
    return this.page.locator('mat-checkbox').filter({ hasText: label });
  }

  /** Locator for a response type checkbox by label text. */
  responseTypeCheckbox(label: RegExp | string): Locator {
    return this.page.locator('mat-checkbox').filter({ hasText: label });
  }

  get tokenAuthMethodSelect(): Locator {
    return this.page.locator('mat-select[name="tokenAuthMethod"]');
  }

  tokenAuthMethodOption(label: RegExp | string): Locator {
    return this.page.locator('mat-option').filter({ hasText: label });
  }

  get refreshTokenSection(): Locator {
    return this.page.getByText('Disable Refresh Token Rotation');
  }

  /** Token exchange scope inheritance toggle (app-level). */
  get inheritanceToggle(): Locator {
    return this.page.getByRole('switch', { name: /inherit/i });
  }

  /** Token exchange scope handling select (app-level, visible after unchecking inherit). */
  get tokenExchangeScopeSelect(): Locator {
    return this.page.locator('mat-select[name="tokenExchangeScopeHandling"]');
  }

  /** Get the displayed text of the token auth method select. */
  async getTokenAuthMethodText(): Promise<string> {
    return (await this.tokenAuthMethodSelect.textContent()) ?? '';
  }
}
