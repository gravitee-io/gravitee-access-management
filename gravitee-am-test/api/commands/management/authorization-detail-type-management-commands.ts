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
import { getAuthorizationDetailTypeApi } from './service/utils';
import { ApiResponse } from '../../management/runtime';
import { AuthorizationDetailType } from '@management-models/AuthorizationDetailType';
import { NewAuthorizationDetailType } from '@management-models/NewAuthorizationDetailType';

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
