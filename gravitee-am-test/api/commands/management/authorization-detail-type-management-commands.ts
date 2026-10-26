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
import { getAuthorizationDetailTypeApi, getDomainManagerUrl } from './service/utils';
import { performPatch } from '@gateway-commands/oauth-oidc-commands';
import { ApiResponse } from '../../management/runtime';
import { AuthorizationDetailType } from '@management-models/AuthorizationDetailType';
import { AuthorizationDetailTypeCursorPage } from '@management-models/AuthorizationDetailTypeCursorPage';
import { ListAuthorizationDetailTypesRequest } from '@management-apis/AuthorizationDetailTypeApi';
import { NewAuthorizationDetailType } from '@management-models/NewAuthorizationDetailType';
import { PatchAuthorizationDetailType } from '@management-models/PatchAuthorizationDetailType';

export const createAuthorizationDetailType = (
  domainId: string,
  accessToken: string,
  body: NewAuthorizationDetailType,
): Promise<AuthorizationDetailType> =>
  getAuthorizationDetailTypeApi(accessToken).createAuthorizationDetailType({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    newAuthorizationDetailType: body,
  });

export const createAuthorizationDetailTypeRaw = (
  domainId: string,
  accessToken: string,
  body: NewAuthorizationDetailType,
): Promise<ApiResponse<AuthorizationDetailType>> =>
  getAuthorizationDetailTypeApi(accessToken).createAuthorizationDetailTypeRaw({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    newAuthorizationDetailType: body,
  });

export const getAuthorizationDetailType = (
  domainId: string,
  accessToken: string,
  authorizationDetailTypeId: string,
): Promise<AuthorizationDetailType> =>
  getAuthorizationDetailTypeApi(accessToken).getAuthorizationDetailType({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    authorizationDetailTypeId,
  });

export const patchAuthorizationDetailType = (
  domainId: string,
  accessToken: string,
  authorizationDetailTypeId: string,
  body: PatchAuthorizationDetailType,
): Promise<AuthorizationDetailType> =>
  getAuthorizationDetailTypeApi(accessToken).patchAuthorizationDetailType({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    authorizationDetailTypeId,
    patchAuthorizationDetailType: body,
  });

export const patchAuthorizationDetailTypeJson = (
  domainId: string,
  accessToken: string,
  authorizationDetailTypeId: string,
  body: Record<string, unknown>,
) =>
  performPatch(getDomainManagerUrl(domainId), `/authorization-detail-types/${authorizationDetailTypeId}`, body, {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${accessToken}`,
  });

export type ListAuthorizationDetailTypesParams = Omit<ListAuthorizationDetailTypesRequest, 'organizationId' | 'environmentId' | 'domain'>;

export const listAuthorizationDetailTypes = (
  domainId: string,
  accessToken: string,
  params: ListAuthorizationDetailTypesParams = {},
): Promise<AuthorizationDetailTypeCursorPage> =>
  getAuthorizationDetailTypeApi(accessToken).listAuthorizationDetailTypes({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    ...params,
  });

export const followNextCursor = (domainId: string, accessToken: string, nextCursor: string): Promise<AuthorizationDetailTypeCursorPage> => {
  const query = new URLSearchParams(nextCursor.substring(nextCursor.indexOf('?') + 1));
  const number = (name: string) => (query.has(name) ? Number(query.get(name)) : undefined);
  return listAuthorizationDetailTypes(domainId, accessToken, {
    limit: number('limit'),
    sort: (query.get('sort') ?? undefined) as ListAuthorizationDetailTypesParams['sort'],
    dir: (query.get('dir') ?? undefined) as ListAuthorizationDetailTypesParams['dir'],
    page: number('page'),
    q: query.get('q') ?? undefined,
    cursor: query.get('cursor') ?? undefined,
  });
};
