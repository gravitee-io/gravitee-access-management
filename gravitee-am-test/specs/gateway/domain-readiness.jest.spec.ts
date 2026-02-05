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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, startDomain } from '@management-commands/domain-management-commands';
import { getDomainState, getAllDomainStates, isDomainReady, waitForDomainReady } from '@gateway-commands/monitoring-commands';
import { setup } from '../test-fixture';
import { uniqueName } from '@utils-commands/misc';

setup(60000);

let accessToken: string;
let domain: any;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  domain = await createDomain(accessToken, uniqueName('readiness', true), 'Domain readiness test');
  await startDomain(domain.id, accessToken);
});

afterAll(async () => {
  await safeDeleteDomain(domain?.id, accessToken);
});

describe('Domain readiness endpoint', () => {
  it('should return domain state after domain is started', async () => {
    const state = await waitForDomainReady(domain.id, { timeoutMillis: 30000 });

    expect(state).toBeDefined();
    expect(state.stable).toBe(true);
    expect(state.synchronized).toBe(true);
    expect(state.status).toBe('DEPLOYED');
    expect(state.creationState).toBeDefined();
    expect(state.syncState).toBeDefined();
  });

  it('should report domain as ready via isDomainReady', async () => {
    const ready = await isDomainReady(domain.id);
    expect(ready).toBe(true);
  });

  it('should return false for unknown domain', async () => {
    const ready = await isDomainReady('non-existent-domain-id');
    expect(ready).toBe(false);
  });

  it('should return all domain states', async () => {
    const states = await getAllDomainStates();
    expect(states).toBeDefined();
    expect(states[domain.id]).toBeDefined();
    expect(states[domain.id].stable).toBe(true);
  });

  it('should return domain state with plugin details via getDomainState', async () => {
    const state = await getDomainState(domain.id);

    expect(state).toBeDefined();
    expect(state.status).toBe('DEPLOYED');
    expect(typeof state.lastSync).toBe('number');
    expect(typeof state.stable).toBe('boolean');
    expect(typeof state.synchronized).toBe('boolean');
  });
});
