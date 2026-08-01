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

import { CockpitQueueEntry, sendCockpitCommand, waitForCockpitReply } from '@cloud-commands/cockpit-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';

export interface CloudOrganizationFixture {
  /** Id of the organization Cockpit created. */
  organizationId: string;
  /** Token scoped to that organization, held by a user with ORGANIZATION_PRIMARY_OWNER there. */
  accessToken: string;
  /** Re-issue the ORGANIZATION command for this organization, merging in extra payload fields. */
  resync: (payload?: Record<string, any>) => Promise<CockpitQueueEntry>;
  cleanup: () => Promise<void>;
}

/**
 * Creates an organization the way Cockpit does — an arbitrary id and hrid, not DEFAULT — and returns a
 * token scoped to it.
 *
 * The token works because the login is scoped with `?org=<id>`: the management API adds the identity
 * providers declared in gravitee.yml to whichever organization is being logged into, then creates the
 * user in that organization and grants the role the provider maps. The stack must therefore declare an
 * inline provider whose user maps to ORGANIZATION_PRIMARY_OWNER (see docker-compose.cloud.yml) — that
 * is a system role held on the platform, so it resolves for any organization, unlike the per-org
 * default roles.
 *
 * `name` MUST be unique per spec file and is used verbatim as the organization id. Organizations cannot
 * be deleted — there is no endpoint and no OrganizationService.delete — so a randomised id would leave a
 * new organization, its default roles, entrypoints, admin user, membership, system reporter and
 * `reporter_audits_<id>` collection behind on every run, growing without bound. A fixed id makes the
 * command an upsert instead, so repeated runs reuse the same records. Anything that IS deletable must
 * still be removed in the caller's cleanup.
 *
 * Managed-cloud stack only (local-stack.sh --cloud).
 */
export const setupCloudOrganizationFixture = async (name: string): Promise<CloudOrganizationFixture> => {
  const organizationId = name;

  const resync = async (payload: Record<string, any> = {}): Promise<CockpitQueueEntry> => {
    const commandId = await sendCockpitCommand({
      type: 'ORGANIZATION',
      payload: {
        id: organizationId,
        name: `Cloud test organization ${organizationId}`,
        hrids: [organizationId],
        ...payload,
      },
    });
    return waitForCockpitReply(commandId);
  };

  const reply = await resync();
  if (reply.commandStatus !== 'SUCCEEDED') {
    throw new Error(`Cockpit refused to create organization ${organizationId}: ${reply.errorDetails}`);
  }

  const accessToken = await requestAdminAccessToken(organizationId);

  return {
    organizationId,
    accessToken,
    resync,
    // The organization itself cannot be removed, so reset the only state a run mutates: re-issuing the
    // command without a license clears it, leaving the organization as the next run expects to find it.
    cleanup: () => resync().then(() => undefined),
  };
};
