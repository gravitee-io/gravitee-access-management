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
import { getCurrentUser } from '@management-commands/organisation-user-commands';
import { getGroupApi } from '@management-commands/service/utils';
import { Group } from '@management-models/Group';
import { uniqueName } from '@utils-commands/misc';
import { CloudOrganizationFixture, setupCloudOrganizationFixture } from './cloud-organization-fixture';

export interface CloudOrgGroupMembersFixture {
  /** Organization that owns the group. */
  home: CloudOrganizationFixture;
  /** Second organization, whose owner is also passed as a member of the home group. */
  foreign: CloudOrganizationFixture;
  homeOwnerId: string;
  foreignOwnerId: string;
  /** Creates a group in the home organization as its owner, with the given member ids. */
  createHomeGroup: (members: string[]) => Promise<Group>;
  /** Raw JSON of GET /organizations/{home}/groups/{group}/members. */
  listHomeGroupMembers: (groupId: string) => Promise<any>;
  cleanup: () => Promise<void>;
}

/**
 * Two managed-cloud organizations, each with its own primary owner. Organization ids are fixed because
 * organizations cannot be deleted (see setupCloudOrganizationFixture). Managed-cloud stack only.
 */
export const setupCloudOrgGroupMembersFixture = async (): Promise<CloudOrgGroupMembersFixture> => {
  const home = await setupCloudOrganizationFixture('org-group-members-home');
  const foreign = await setupCloudOrganizationFixture('org-group-members-foreign');
  const groupIds: string[] = [];

  const homeOwnerId = (await getCurrentUser(home.accessToken)).sub;
  const foreignOwnerId = (await getCurrentUser(foreign.accessToken)).sub;

  const createHomeGroup = async (members: string[]) => {
    const response = await getGroupApi(home.accessToken).createPlatformGroupRaw({
      organizationId: home.organizationId,
      newGroup: { name: uniqueName('cross-org-group', true), members },
    });
    const group: Group = await response.raw.json();
    groupIds.push(group.id);
    return group;
  };

  const listHomeGroupMembers = async (groupId: string) =>
    (await getGroupApi(home.accessToken).listGroupMembersRaw({ organizationId: home.organizationId, group: groupId })).raw.json();

  const cleanup = async () => {
    for (const groupId of groupIds) {
      await getGroupApi(home.accessToken)
        .deleteOrganizationGroup({ organizationId: home.organizationId, group: groupId })
        .catch((error) => console.error('Failed to delete organization group:', error));
    }
    await home.cleanup();
    await foreign.cleanup();
  };

  return { home, foreign, homeOwnerId, foreignOwnerId, createHomeGroup, listHomeGroupMembers, cleanup };
};
