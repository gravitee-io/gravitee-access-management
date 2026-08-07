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
    const id = 'cloud-org-validation';
    const commandId = await sendCockpitCommand({
      type: 'ORGANIZATION',
      payload: { id, name: 'Command validation org', hrids: [id] },
    });

    const reply = await waitForCockpitReply(commandId);

    expect(reply.commandStatus).toBe('SUCCEEDED');
    // Note: organizations have no delete endpoint in the management API
  });

  // A host-less GATEWAY access point used to persist an entrypoint with the url "https://null" and no
  // name, and that nameless record then broke the organization-wide entrypoint listing with a 500.
  // Cockpit is now told the command failed instead of believing the gateway URL was provisioned.
  describe('ENVIRONMENT gateway access points', () => {
    const organizationId = 'cloud-env-accesspoint-validation';
    const missingHostError = 'GATEWAY access point with a missing or blank host';

    // Fixed ids for the same reason as the organization above: neither organizations nor environments
    // can be deleted, so a randomised id would leave a new set behind on every run.
    const sendEnvironment = async (id: string, accessPoints: unknown[]) => {
      const commandId = await sendCockpitCommand({
        type: 'ENVIRONMENT',
        payload: { id, organizationId, hrids: [id], name: `Access point validation ${id}`, accessPoints },
      });
      return waitForCockpitReply(commandId);
    };

    beforeAll(async () => {
      const commandId = await sendCockpitCommand({
        type: 'ORGANIZATION',
        payload: { id: organizationId, name: 'Access point validation org', hrids: [organizationId] },
      });
      const reply = await waitForCockpitReply(commandId);
      expect(reply.commandStatus).toBe('SUCCEEDED');
    });

    it('rejects a GATEWAY access point with a null host', async () => {
      const reply = await sendEnvironment('cloud-env-ap-null-host', [{ target: 'GATEWAY', host: null, secured: true }]);

      expect(reply.commandStatus).toBe('ERROR');
      expect(reply.errorDetails).toContain(missingHostError);
    });

    it('rejects a GATEWAY access point with a blank host', async () => {
      const reply = await sendEnvironment('cloud-env-ap-blank-host', [{ target: 'GATEWAY', host: '  ' }]);

      expect(reply.commandStatus).toBe('ERROR');
      expect(reply.errorDetails).toContain(missingHostError);
    });

    it('rejects the whole command when only one of two GATEWAY access points has a host', async () => {
      const reply = await sendEnvironment('cloud-env-ap-mixed-hosts', [
        { target: 'GATEWAY', host: 'gw-valid.example.com' },
        { target: 'GATEWAY', host: null },
      ]);

      expect(reply.commandStatus).toBe('ERROR');
      expect(reply.errorDetails).toContain(missingHostError);
    });

    it('accepts a CONSOLE access point with no host, since it never becomes an entrypoint', async () => {
      const reply = await sendEnvironment('cloud-env-ap-console-no-host', [{ target: 'CONSOLE', host: null }]);

      expect(reply.commandStatus).toBe('SUCCEEDED');
    });
  });
});
