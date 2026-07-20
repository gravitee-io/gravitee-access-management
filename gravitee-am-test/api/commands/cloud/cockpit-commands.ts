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

import { retryUntil } from '@utils-commands/retry';

/**
 * Drives the cockpit mock (gravitee-am-cockpit-mock) control API so cloud specs can push Cockpit
 * commands at the management API over its WebSocket connector. Only available when the stack is
 * brought up in managed-cloud mode (local-stack.sh --cloud / stack:ci:setup:cloud:*).
 */

const baseUrl = process.env.AM_COCKPIT_MOCK_URL;

export interface CockpitCommand {
  type: string;
  payload: Record<string, any>;
}

/** POST a command toward AM. Returns the generated command id; AM's reply lands on the mock queue. */
export const sendCockpitCommand = async (command: CockpitCommand): Promise<string> => {
  const response = await fetch(`${baseUrl}/_control/send`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(command),
  });
  if (response.status !== 200) {
    throw new Error(`cockpit mock /_control/send returned ${response.status}: ${await response.text()}`);
  }
  return (await response.json()).id;
};

/** Whether an AM instance is currently connected to the cockpit mock. */
export const isCockpitConnected = async (): Promise<boolean> => {
  const response = await fetch(`${baseUrl}/_control/status`);
  return response.status === 200 && (await response.json()).connected === true;
};

/** Wait until AM has established its WebSocket connection to the cockpit mock. */
export const waitForCockpitConnection = (options?: { timeoutMillis?: number; intervalMillis?: number }): Promise<boolean> =>
  retryUntil(() => isCockpitConnected().catch(() => false), (connected) => connected === true, {
    timeoutMillis: options?.timeoutMillis ?? 30000,
    intervalMillis: options?.intervalMillis ?? 1000,
  });
