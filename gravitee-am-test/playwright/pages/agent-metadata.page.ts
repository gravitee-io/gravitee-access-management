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
import { expect } from '@playwright/test';
import { BasePage } from './base.page';

/** Page object for Application > Agent Metadata tab. */
export class AgentMetadataPage extends BasePage {
  /** Navigate directly to an application's agent metadata page. */
  async navigateTo(domainId: string, appId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/applications/${appId}/agent-metadata`);
  }

  /** Assert the no-URL state is displayed. */
  async expectNoUrlState(): Promise<void> {
    const noUrlState = this.page.locator('[data-testid="agentCardState"]').filter({ hasText: /no agentcard url/i });
    await expect(noUrlState).toBeVisible();
  }

  /** Assert that the page shows either loaded content or an error state (not the no-URL state). */
  async expectContentOrError(): Promise<void> {
    const loaded = this.page.locator('[data-testid="agentCardSummary"], .agent-card-state:has(mat-icon[color="warn"])');
    // Extended timeout: agent card URL fetch hits an external service; network latency varies.
    await expect(loaded.first()).toBeVisible({ timeout: 15000 });
  }
}
