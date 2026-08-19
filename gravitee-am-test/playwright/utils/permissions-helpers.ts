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

import type { Page } from '@playwright/test';
import { createOrganisationUser } from '@management-commands/organisation-user-commands';
import { LoginPage } from '../pages/login.page';
import { quietly, uniqueTestName } from './fixture-helpers';
import { UI_USER_PASSWORD } from './test-constants';

/**
 * Playwright runs spec files in separate worker processes and faker reseeds identically in each,
 * so uniqueTestName() alone yields the same sequence per worker. Fixtures in this area share their
 * prefixes, so without this the workers collide on "already exists". The process id separates them.
 */
export const workerScope = (prefix: string) => `${prefix}-${process.pid}`;

/** A console user a test can sign in as. */
export interface ConsolePersona {
  username: string;
  password: string;
  userId: string;
}

/**
 * Creates an organization user that can sign in to the Console.
 *
 * Creating the user is enough on its own: AM grants the default ORGANIZATION_USER role at
 * creation, and holding an organization role is what makes the Console reachable at all. Roles at
 * domain or application level are layered on afterwards as memberships.
 */
export async function createConsolePersona(adminToken: string, label: string): Promise<ConsolePersona> {
  const username = uniqueTestName(workerScope(label)).toLowerCase();
  const user = await quietly(() =>
    createOrganisationUser(adminToken, {
      firstName: label,
      lastName: 'Persona',
      email: `${username}@test.com`,
      username,
      password: UI_USER_PASSWORD,
      preRegistration: false,
    }),
  );
  return { username, password: UI_USER_PASSWORD, userId: user.id };
}

/**
 * Signs in through the Console login form as the given persona.
 *
 * `LoginPage.login()` waits for a URL containing environments/dashboard/domains, which assumes the
 * user has somewhere to land. A user holding no domain at all — exactly the case these tests need
 * to cover — is left on the application root instead, so that wait never resolves. Waiting for the
 * login route to be *left* behind works for every persona regardless of what they can reach.
 */
export async function signInToConsole(page: Page, persona: ConsolePersona): Promise<void> {
  const login = new LoginPage(page);
  await page.goto('/');
  await page.waitForURL(/.*login.*|.*auth\/authorize.*/i);

  await login.usernameInput.waitFor({ state: 'visible' });
  await login.usernameInput.fill(persona.username);
  await login.passwordInput.fill(persona.password);
  await login.signInButton.click();

  await page.waitForURL((url) => !/\/login/.test(url.pathname) && !/\/oauth\/authorize/.test(url.pathname));
}

// Re-exported from the suite-wide helper so there is one definition of this locator.
export { submenuItem } from './selectors';
