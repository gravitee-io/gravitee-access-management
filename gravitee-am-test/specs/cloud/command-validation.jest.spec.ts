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

import { beforeAll, describe, expect, it } from '@jest/globals';
import { sendCockpitCommand, waitForCockpitConnection, waitForCockpitReply } from '@cloud-commands/cockpit-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../test-fixture';

setup(120000);

describe('Cloud command payload validation (Cockpit -> AM)', () => {
  beforeAll(async () => {
    await waitForCockpitConnection();
  });

  it('rejects an ORGANIZATION command with an empty payload as ERROR', async () => {
    const commandId = await sendCockpitCommand({ type: 'ORGANIZATION', payload: {} });

    const reply = await waitForCockpitReply(commandId);

    expect(reply.commandStatus).toBe('ERROR');
    expect(reply.errorDetails).toContain('required field');
  });

  it('accepts a well-formed ORGANIZATION command as SUCCEEDED', async () => {
    const id = uniqueName('org-validation', true);
    const commandId = await sendCockpitCommand({
      type: 'ORGANIZATION',
      payload: { id, name: 'Command validation org', hrids: [id] },
    });

    const reply = await waitForCockpitReply(commandId);

    expect(reply.commandStatus).toBe('SUCCEEDED');
    // Note: organizations have no delete endpoint in the management API; this upserted org is left in place.
  });
});
