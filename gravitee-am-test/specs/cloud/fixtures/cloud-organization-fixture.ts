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

import { CockpitQueueEntry, cockpitSignIn, sendCockpitCommand, waitForCockpitReply } from '@cloud-commands/cockpit-commands';

export interface CloudOrganizationFixture {
  /** Id of the organization Cockpit created. */
  organizationId: string;
  /**
   * The organization's first environment. Cockpit always creates one alongside the organization, and AM
   * requires it: the SSO endpoint resolves the token's `env` claim before it will issue a session.
   */
  environmentId: string;
  /** Cockpit id of the user that owns it. */
  userId: string;
  /** Token for that user, obtained through the Cockpit SSO endpoint. */
  accessToken: string;
  /** Re-issue the ORGANIZATION command for this organization, merging in extra payload fields. */
  resync: (payload?: Record<string, any>) => Promise<CockpitQueueEntry>;
  /**
   * Grant the owner ENVIRONMENT_PRIMARY_OWNER on an environment, as Cockpit's createEnvironmentBatch
   * does. Call it once after the ENVIRONMENT command for that environment has been acknowledged.
   */
  grantEnvironmentOwnership: (environmentId: string) => Promise<CockpitQueueEntry>;
  cleanup: () => Promise<void>;
}

/**
 * Provisions an organization exactly the way Cockpit does, then signs in as its owner.
 *
 * Mirrors Cockpit's `BatchFactory.createOrganizationBatch`: ORGANIZATION, then USER, then MEMBERSHIP,
 * each awaited before the next. The order matters — MEMBERSHIP resolves the user by external id plus
 * source `cockpit`, and any per-organization role it names only exists once ORGANIZATION has created
 * that organization's default roles.
 *
 * The owner gets ORGANIZATION_PRIMARY_OWNER, a platform-level system role, which is what Cockpit grants
 * in managed cloud (`RoleService.resolveOrganizationOwnerRoleNameForInstallation` never sees a default
 * organization there, so it never falls back to ORGANIZATION_OWNER). Note only one user may hold it per
 * organization: `MembershipServiceImpl.checkRole` raises SinglePrimaryOwnerException on a second one, so
 * an organization cannot mix this fixture with an inline-IDP admin login.
 *
 * `name` MUST be unique per spec file and is used verbatim as the organization id. Organizations cannot
 * be deleted — there is no endpoint, no OrganizationService.delete, and AM has no DELETE_ORGANIZATION
 * command handler yet — so a randomised id would leave a new organization, its default roles,
 * entrypoints, owner, membership, system reporter and `reporter_audits_<id>` collection behind on every
 * run, growing without bound. A fixed id makes every command an upsert instead, so repeated runs reuse
 * the same records. Anything that IS deletable must still be removed in the caller's cleanup.
 *
 * Managed-cloud stack only (local-stack.sh --cloud).
 */
export const setupCloudOrganizationFixture = async (name: string): Promise<CloudOrganizationFixture> => {
  const organizationId = name;
  const environmentId = `${name}-home`;
  const userId = `${name}-owner`;

  const awaitCommand = async (type: string, payload: Record<string, any>): Promise<CockpitQueueEntry> => {
    const commandId = await sendCockpitCommand({ type, payload });
    const reply = await waitForCockpitReply(commandId);
    if (reply.commandStatus !== 'SUCCEEDED') {
      throw new Error(`Cockpit ${type} command failed for organization ${organizationId}: ${reply.errorDetails}`);
    }
    return reply;
  };

  const resync = (payload: Record<string, any> = {}): Promise<CockpitQueueEntry> =>
    awaitCommand('ORGANIZATION', {
      id: organizationId,
      name: `Cloud test organization ${organizationId}`,
      hrids: [organizationId],
      ...payload,
    });

  // Cockpit's createEnvironmentBatch re-sends USER before the environment membership; the user already
  // exists by then and createOrUpdate makes the repeat a no-op, so only the membership is reproduced.
  const grantEnvironmentOwnership = (environmentId: string): Promise<CockpitQueueEntry> =>
    awaitCommand('MEMBERSHIP', {
      organizationId,
      referenceType: 'ENVIRONMENT',
      referenceId: environmentId,
      userId,
      role: 'ENVIRONMENT_PRIMARY_OWNER',
    });

  // createOrganizationBatch: ORGANIZATION, USER, MEMBERSHIP
  await resync();
  await awaitCommand('USER', {
    id: userId,
    username: userId,
    firstName: 'Cloud',
    lastName: 'Owner',
    email: `${userId}@example.com`,
    organizationId,
  });
  await awaitCommand('MEMBERSHIP', {
    organizationId,
    referenceType: 'ORGANIZATION',
    referenceId: organizationId,
    userId,
    role: 'ORGANIZATION_PRIMARY_OWNER',
  });

  // createEnvironmentBatch: ENVIRONMENT then MEMBERSHIP (the USER command in between is a no-op here).
  // Not optional — CockpitAuthenticationFilter resolves the token's `env` claim through
  // environmentService.findById, which errors when the environment is missing, and the filter turns any
  // exception into a 403. An organization with no environment cannot be signed into.
  await awaitCommand('ENVIRONMENT', {
    id: environmentId,
    organizationId,
    hrids: [environmentId],
    name: `Cloud test home environment ${environmentId}`,
  });
  await grantEnvironmentOwnership(environmentId);

  const accessToken = await cockpitSignIn({ sub: userId, organizationId, environmentId });

  return {
    organizationId,
    environmentId,
    userId,
    accessToken,
    resync,
    grantEnvironmentOwnership,
    // The organization itself cannot be removed, so reset the only state a run mutates: re-issuing the
    // command without a license clears it, leaving the organization as the next run expects to find it.
    cleanup: () => resync().then(() => undefined),
  };
};
