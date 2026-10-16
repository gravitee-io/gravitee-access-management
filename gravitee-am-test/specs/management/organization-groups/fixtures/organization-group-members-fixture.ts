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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createOrganisationUser, deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { bulkCreateOrgUsers } from '@management-commands/user-management-commands';
import { getGroupApi } from '@management-commands/service/utils';
import { BulkResponse } from '@management-models/BulkResponse';
import { Group } from '@management-models/Group';
import { User } from '@management-models/User';
import { uniqueName } from '@utils-commands/misc';

export const MEMBER_PASSWORD = 'SomeP@ssw0rd';

export interface OrganizationGroupMembersFixture {
  accessToken: string;
  /** Organization user of the gravitee identity provider, created with a password. */
  member: User;
  group: Group;
  /** Raw JSON of GET /organizations/{org}/groups/{group}/members. */
  listMembers: () => Promise<any>;
  /** Bulk-creates one organization user with a password; the user is removed in cleanup. */
  bulkCreateMember: () => Promise<BulkResponse>;
  cleanup: () => Promise<void>;
}

const newMember = () => {
  const username = uniqueName('grp-member', true).toLowerCase();
  return { username, email: `${username}@mail.com`, firstName: 'Group', lastName: 'Member', password: MEMBER_PASSWORD };
};

export const setupOrganizationGroupMembersFixture = async (): Promise<OrganizationGroupMembersFixture> => {
  const organizationId = process.env.AM_DEF_ORG_ID;
  const userIds: string[] = [];
  let accessToken: string | null = null;
  let group: Group | null = null;

  const cleanup = async () => {
    if (!accessToken) {
      return;
    }
    if (group?.id) {
      await getGroupApi(accessToken)
        .deleteOrganizationGroup({ organizationId, group: group.id })
        .catch((error) => console.error('Failed to delete organization group:', error));
    }
    for (const userId of userIds) {
      await deleteOrganisationUser(accessToken, userId).catch((error) => console.error('Failed to delete organization user:', error));
    }
  };

  try {
    accessToken = await requestAdminAccessToken();
    const member = await createOrganisationUser(accessToken, newMember());
    userIds.push(member.id);

    const created = await getGroupApi(accessToken).createPlatformGroupRaw({
      organizationId,
      newGroup: { name: uniqueName('org-group', true), members: [member.id] },
    });
    group = await created.raw.json();

    const listMembers = async () => (await getGroupApi(accessToken).listGroupMembersRaw({ organizationId, group: group.id })).raw.json();

    const bulkCreateMember = async () => {
      const response = await bulkCreateOrgUsers(accessToken, [newMember()]);
      response.results.filter((result) => result.body?.id).forEach((result) => userIds.push(result.body.id));
      return response;
    };

    return { accessToken, member, group, listMembers, bulkCreateMember, cleanup };
  } catch (error) {
    await cleanup();
    throw error;
  }
};
