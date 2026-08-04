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

import { Page } from '@playwright/test';
import { CockpitSsoRequest, mintCockpitSsoToken } from '@cloud-commands/cockpit-commands';

/**
 * Sign the Playwright browser into the Console the way Cockpit does: mint an SSO token, hit
 * `/management/auth/cockpit`, and follow the redirect onto the Console origin. Sets the
 * `Auth-Graviteeio-AM` cookie on the management API host so subsequent Console API calls succeed.
 *
 * `redirectUri` is required here — the local stack serves the Console and the management API on
 * different ports, so a relative redirect would 404 on the API host.
 */
export const cockpitBrowserSignIn = async (page: Page, sso: CockpitSsoRequest & { redirectUri: string }): Promise<void> => {
  const token = await mintCockpitSsoToken({
    ...sso,
    // Browser navigation can take longer than Cockpit's 10s production TTL.
    ttlSeconds: sso.ttlSeconds ?? 60,
  });

  const managementUrl = process.env.AM_MANAGEMENT_URL;
  if (!managementUrl) {
    throw new Error('AM_MANAGEMENT_URL must be set for Cockpit browser sign-in');
  }

  await page.goto(`${managementUrl}/management/auth/cockpit?token=${encodeURIComponent(token)}`);
  await page.waitForURL((url) => url.origin === new URL(sso.redirectUri).origin && /\/environments\//.test(url.pathname));
};
