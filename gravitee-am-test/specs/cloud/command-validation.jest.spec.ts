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
      // The GATEWAY access point alongside it is what makes the payload otherwise valid; on its own a
      // CONSOLE access point leaves the environment with no gateway URL and is rejected below.
      const reply = await sendEnvironment('cloud-env-ap-console-no-host', [
        { target: 'CONSOLE', host: null },
        { target: 'GATEWAY', host: 'gw-console-companion.example.com' },
      ]);

      expect(reply.commandStatus).toBe('SUCCEEDED');
    });

    // The counterpart to the rejections above: the guard must not turn away a GATEWAY access point
    // that is perfectly usable, which is the regression it could plausibly introduce.
    it('accepts a GATEWAY access point that has a host', async () => {
      const reply = await sendEnvironment('cloud-env-ap-valid-gateway', [{ target: 'GATEWAY', host: 'gw-am7443.example.com' }]);

      expect(reply.commandStatus).toBe('SUCCEEDED');
    });
  });

  // A cloud environment resolves its gateway URL from the access points and from nothing else, so a
  // payload carrying none would leave it with no entrypoint at all. Cockpit is told the command failed
  // instead of ending up with an environment it cannot route to.
  describe('ENVIRONMENT without a usable gateway access point', () => {
    const organizationId = 'cloud-env-no-accesspoint-validation';

    const sendEnvironment = async (id: string, accessPoints?: unknown[]) => {
      const commandId = await sendCockpitCommand({
        type: 'ENVIRONMENT',
        payload: {
          id,
          organizationId,
          hrids: [id],
          name: `No access point validation ${id}`,
          ...(accessPoints === undefined ? {} : { accessPoints }),
        },
      });
      return waitForCockpitReply(commandId);
    };

    beforeAll(async () => {
      const commandId = await sendCockpitCommand({
        type: 'ORGANIZATION',
        payload: { id: organizationId, name: 'No access point validation org', hrids: [organizationId] },
      });
      const reply = await waitForCockpitReply(commandId);
      expect(reply.commandStatus).toBe('SUCCEEDED');
    });

    it('rejects a command with no accessPoints field at all', async () => {
      const reply = await sendEnvironment('cloud-env-no-ap-absent');

      expect(reply.commandStatus).toBe('ERROR');
      expect(reply.errorDetails).toContain('missing or empty accessPoints');
    });

    it('rejects a command with an empty accessPoints array', async () => {
      const reply = await sendEnvironment('cloud-env-no-ap-empty', []);

      expect(reply.commandStatus).toBe('ERROR');
      expect(reply.errorDetails).toContain('missing or empty accessPoints');
    });

    it('rejects a command whose only access point is a CONSOLE one', async () => {
      const reply = await sendEnvironment('cloud-env-no-ap-console-only', [{ target: 'CONSOLE', host: 'console-only.example.com' }]);

      expect(reply.commandStatus).toBe('ERROR');
      expect(reply.errorDetails).toContain('no GATEWAY access point');
    });

    // Resolution drops the generated default whenever an overriding access point exists, so an
    // environment holding only overriding ones would resolve to nothing once the default is gone.
    it('rejects a command whose GATEWAY access points are all overriding', async () => {
      const reply = await sendEnvironment('cloud-env-no-ap-all-overriding', [
        { target: 'GATEWAY', host: 'auth.acme.example.com', overriding: true },
        { target: 'GATEWAY', host: 'login.acme.example.com', overriding: true },
      ]);

      expect(reply.commandStatus).toBe('ERROR');
      expect(reply.errorDetails).toContain('every GATEWAY access point is overriding');
    });

    it('accepts an overriding access point alongside the generated default', async () => {
      const reply = await sendEnvironment('cloud-env-no-ap-override-plus-default', [
        { target: 'GATEWAY', host: 'env-acme.example.com' },
        { target: 'GATEWAY', host: 'auth.acme.example.com', overriding: true },
      ]);

      expect(reply.commandStatus).toBe('SUCCEEDED');
    });
  });
});
