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

import { expect } from '@jest/globals';

/** Admin API base URL (host:port) for the WireMock instance mapped from `SFR_URL` in CI. */
export const getWireMockAdminBase = (): string => process.env.SFR_URL ?? 'http://localhost:8181';

/**
 * Resets all WireMock scenarios to their initial "Started" state.
 * Call in `beforeAll` for tests that rely on scenario-based stub behaviour.
 */
export const resetAllWireMockScenarios = async (): Promise<void> => {
  const res = await fetch(`${getWireMockAdminBase()}/__admin/scenarios/reset`, { method: 'POST' });
  expect(res.ok).toBe(true);
};

/**
 * Clears the WireMock request journal (best-effort). Use between tests that assert on request counts.
 */
export const clearWireMockRequestJournal = async (): Promise<void> => {
  try {
    await fetch(`${getWireMockAdminBase()}/__admin/requests`, { method: 'DELETE' });
  } catch {
    // Best-effort: local runs may not have WireMock; tests should not depend on this alone.
  }
};

/**
 * Returns the current WireMock request journal payload (`requests` is the list of served requests).
 */
export const fetchWireMockRequestJournal = async (): Promise<unknown> => {
  const res = await fetch(`${getWireMockAdminBase()}/__admin/requests`);
  expect(res.ok).toBe(true);
  return res.json();
};

/**
 * Counts completed GET requests whose URL contains `pathSubstring` (matches journal shape from WireMock 3).
 */
export const countGetRequestsForPathSubstring = (journal: unknown, pathSubstring: string): number => {
  const entries = (journal as { requests?: unknown[] })?.requests ?? [];
  return entries.filter((entry) => {
    const raw = entry as { request?: Record<string, unknown> };
    const req = (raw?.request ?? entry) as Record<string, unknown>;
    const method = String(req?.method ?? '').toUpperCase();
    const url = String(req?.url ?? req?.absoluteUrl ?? req?.path ?? '');
    return method === 'GET' && url.includes(pathSubstring);
  }).length;
};

export type WaitForWireMockGetOptions = {
  /** Total time to poll before failing. */
  timeoutMs?: number;
  /** Delay between journal polls. */
  pollMs?: number;
};

/**
 * Resolves when at least one GET to a URL containing `pathSubstring` appears in the WireMock journal.
 */
export const waitForWireMockGetToPath = async (
  pathSubstring: string,
  options: WaitForWireMockGetOptions = {},
): Promise<void> => {
  const timeoutMs = options.timeoutMs ?? 5000;
  const pollMs = options.pollMs ?? 150;
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const journal = await fetchWireMockRequestJournal();
    if (countGetRequestsForPathSubstring(journal, pathSubstring) > 0) {
      return;
    }
    await new Promise((r) => setTimeout(r, pollMs));
  }
  throw new Error(`No WireMock GET request matched within ${timeoutMs}ms: ${pathSubstring}`);
};
