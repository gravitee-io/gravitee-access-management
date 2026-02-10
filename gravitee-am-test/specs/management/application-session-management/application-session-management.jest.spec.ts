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

import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { Application } from '@management-models/Application';
import { setup } from '../../test-fixture';
import { initFixture, SessionManagementFixture } from './fixtures/session-management-fixture';

setup(200000);

let fixture: SessionManagementFixture;
let app: Application;

beforeAll(async () => {
  fixture = await initFixture();
  app = await fixture.createBrowserApp();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Application Session Management Settings', () => {
  it('should save persistent session enabled when not inherited', async () => {
    const patched = await fixture.patchSessionSettings(app.id, false, true);

    expect(patched.settings.cookieSettings).toBeDefined();
    expect(patched.settings.cookieSettings.inherited).toBe(false);
    expect(patched.settings.cookieSettings.session).toBeDefined();
    expect(patched.settings.cookieSettings.session.persistent).toBe(true);
  });

  it('should save persistent session disabled when not inherited', async () => {
    const patched = await fixture.patchSessionSettings(app.id, false, false);

    expect(patched.settings.cookieSettings).toBeDefined();
    expect(patched.settings.cookieSettings.inherited).toBe(false);
    expect(patched.settings.cookieSettings.session).toBeDefined();
    expect(patched.settings.cookieSettings.session.persistent).toBe(false);
  });

  it('should save inherited cookie settings', async () => {
    const patched = await fixture.patchSessionSettings(app.id, true);

    expect(patched.settings.cookieSettings).toBeDefined();
    expect(patched.settings.cookieSettings.inherited).toBe(true);
  });

  it('should persist cookie settings across GET requests', async () => {
    const patched = await fixture.patchSessionSettings(app.id, false, true);
    const fetched = await fixture.getApplicationSettings(app.id);

    expect(fetched.settings.cookieSettings.inherited).toBe(patched.settings.cookieSettings.inherited);
    expect(fetched.settings.cookieSettings.session.persistent).toBe(patched.settings.cookieSettings.session.persistent);
  });

  it('should toggle persistent session setting', async () => {
    const withPersistent = await fixture.patchSessionSettings(app.id, false, true);
    expect(withPersistent.settings.cookieSettings.session.persistent).toBe(true);

    const withoutPersistent = await fixture.patchSessionSettings(app.id, false, false);
    expect(withoutPersistent.settings.cookieSettings.session.persistent).toBe(false);

    const backToPersistent = await fixture.patchSessionSettings(app.id, false, true);
    expect(backToPersistent.settings.cookieSettings.session.persistent).toBe(true);
  });
});
