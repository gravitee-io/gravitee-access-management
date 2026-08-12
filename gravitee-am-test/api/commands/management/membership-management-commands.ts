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

import { getApplicationApi, getDefaultApi, getDomainApi } from './service/utils';
// Type-only: `@management-models` is deliberately absent from the jest moduleNameMapper in
// api/config/*.config.js, so these imports must stay erasable. Importing a *value* from
// @management-models (an enum object, say) compiles but fails at runtime with
// "Cannot find module". Use string literals for enum members instead.
import type { NewMembership } from '@management-models/NewMembership';
import type { MembershipListItem } from '@management-models/MembershipListItem';

/**
 * A membership is the (member, level, role) triple that grants access in AM. The generated SDK
 * exposes these across four different APIs with ambiguous generated names (addOrUpdateMember,
 * addOrUpdateMember1, addOrUpdateMember2). This module maps them to the level they act on.
 *
 * `role` is always the role **id**, never the role name.
 */

/** Builds a USER membership payload. Use {@link groupMembership} for groups. */
export const userMembership = (memberId: string, roleId: string): NewMembership => ({
  memberId,
  memberType: 'USER',
  role: roleId,
});

/** Builds a GROUP membership payload. AM rejects PRIMARY_OWNER roles assigned this way. */
export const groupMembership = (memberId: string, roleId: string): NewMembership => ({
  memberId,
  memberType: 'GROUP',
  role: roleId,
});

// --- Organization level -------------------------------------------------------------------

export const addOrganizationMembership = (accessToken: string, newMembership: NewMembership): Promise<void> =>
  getDefaultApi(accessToken).addOrUpdateOrganizationMember({
    organizationId: process.env.AM_DEF_ORG_ID,
    newMembership,
  });

export const listOrganizationMemberships = (accessToken: string): Promise<MembershipListItem> =>
  getDefaultApi(accessToken).listOrganizationMembers({
    organizationId: process.env.AM_DEF_ORG_ID,
  });

export const removeOrganizationMembership = (accessToken: string, membershipId: string): Promise<void> =>
  getDefaultApi(accessToken).removeOrganizationMember({
    organizationId: process.env.AM_DEF_ORG_ID,
    member: membershipId,
  });

// --- Domain level -------------------------------------------------------------------------

export const addDomainMembership = (domainId: string, accessToken: string, newMembership: NewMembership): Promise<void> =>
  getDomainApi(accessToken).addOrUpdateMember1({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    newMembership,
  });

export const listDomainMemberships = (domainId: string, accessToken: string): Promise<MembershipListItem> =>
  getDomainApi(accessToken).listMembers({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
  });

export const removeDomainMembership = (domainId: string, accessToken: string, membershipId: string): Promise<void> =>
  getDomainApi(accessToken).removeDomainMember({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    member: membershipId,
  });

// --- Application level --------------------------------------------------------------------

export const addApplicationMembership = (
  domainId: string,
  applicationId: string,
  accessToken: string,
  newMembership: NewMembership,
): Promise<void> =>
  getApplicationApi(accessToken).addOrUpdateMember({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    application: applicationId,
    newMembership,
  });

export const listApplicationMemberships = (domainId: string, applicationId: string, accessToken: string): Promise<MembershipListItem> =>
  getApplicationApi(accessToken).getMembers({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    application: applicationId,
  });

export const removeApplicationMembership = (
  domainId: string,
  applicationId: string,
  accessToken: string,
  membershipId: string,
): Promise<void> =>
  getApplicationApi(accessToken).removeApplicationMember({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    application: applicationId,
    member: membershipId,
  });

// --- Protected resource level ---------------------------------------------------------------

export const addProtectedResourceMembership = (
  domainId: string,
  protectedResourceId: string,
  accessToken: string,
  newMembership: NewMembership,
): Promise<void> =>
  getDomainApi(accessToken).addOrUpdateMember2({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    protectedResource: protectedResourceId,
    newMembership,
  });

export const listProtectedResourceMemberships = (
  domainId: string,
  protectedResourceId: string,
  accessToken: string,
): Promise<MembershipListItem> =>
  getDomainApi(accessToken).getMembers1({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    protectedResource: protectedResourceId,
  });

export const removeProtectedResourceMembership = (
  domainId: string,
  protectedResourceId: string,
  accessToken: string,
  membershipId: string,
): Promise<void> =>
  getDomainApi(accessToken).removeProtectedResourceMember({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    protectedResource: protectedResourceId,
    member: membershipId,
  });

// --- Effective permissions (what the caller resolves to at a given level) --------------------

/**
 * The OpenAPI spec types these endpoints as returning a plain string, so the generator emits a
 * TextApiResponse and the SDK hands back an unparsed body. They actually return a JSON array of
 * flattened `permission_acl` strings (e.g. "domain_read"), so parse here rather than in each test.
 */
const parsePermissions = (raw: string): string[] => JSON.parse(raw);

export const getDomainMemberPermissions = (domainId: string, accessToken: string): Promise<string[]> =>
  getDomainApi(accessToken)
    .getDomainMemberPermissions({
      organizationId: process.env.AM_DEF_ORG_ID,
      environmentId: process.env.AM_DEF_ENV_ID,
      domain: domainId,
    })
    .then(parsePermissions);

export const getApplicationMemberPermissions = (domainId: string, applicationId: string, accessToken: string): Promise<string[]> =>
  getApplicationApi(accessToken)
    .getApplicationMemberPermissions({
      organizationId: process.env.AM_DEF_ORG_ID,
      environmentId: process.env.AM_DEF_ENV_ID,
      domain: domainId,
      application: applicationId,
    })
    .then(parsePermissions);

/**
 * Environment memberships cannot be listed — the environment resource exposes only this
 * permissions endpoint — so this is the sole way to observe what a caller resolves to at the
 * environment tier, and therefore the only way to see memberships created there implicitly.
 */
export const getEnvironmentMemberPermissions = (accessToken: string): Promise<string[]> =>
  getDefaultApi(accessToken)
    .getMemberPermissions({
      organizationId: process.env.AM_DEF_ORG_ID,
      environmentId: process.env.AM_DEF_ENV_ID,
    })
    .then(parsePermissions);
