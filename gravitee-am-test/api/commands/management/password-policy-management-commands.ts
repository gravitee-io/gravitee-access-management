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

import { getIdpApi, getPasswordPolicyApi,getUserApi } from './service/utils';
import { UpdatePasswordPolicy } from '../../management/models/UpdatePasswordPolicy';
import { NewPasswordPolicy } from '../../management/models';

export const createPasswordPolicy = (domainId: string, accessToken: string, body: NewPasswordPolicy) =>
  getPasswordPolicyApi(accessToken).createPasswordPolicy({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    newPasswordPolicy: body,
  });

export const getPasswordPolicy = (domainId: string, accessToken: string, policyId: string) =>
  getPasswordPolicyApi(accessToken).getPasswordPolicy({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    policy: policyId,
  });

export const getAllPasswordPolicies = (domainId: string, accessToken: string) =>
  getPasswordPolicyApi(accessToken).listPasswordPolicies({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
  });

export const getAllPasswordPoliciesRaw = (domainId: string, accessToken: string) =>
  getPasswordPolicyApi(accessToken).listPasswordPoliciesRaw({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
  });

export const updatePasswordPolicy = (domainId: string, accessToken: string, policyId: string, body: UpdatePasswordPolicy) =>
  getPasswordPolicyApi(accessToken).updatePasswordPolicy({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    policy: policyId,
    updatePasswordPolicy: body,
  });

export const setPasswordPolicyDefault = (domainId: string, accessToken: string, policyId: string) =>
  getPasswordPolicyApi(accessToken).setDefaultPolicy({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    policy: policyId,
  });

export const deletePasswordPolicy = (domainId: string, accessToken: string, policyId: string) =>
  getPasswordPolicyApi(accessToken).deletePasswordPolicy({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    policy: policyId,
  });

export const assignPasswordPolicyToIdp = (domainId: string, accessToken: string, idpId: string, policyId: string) =>
  getIdpApi(accessToken).assignPasswordPolicyToIdp({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    identity: idpId,
    assignPasswordPolicy: {passwordPolicy: policyId},
  });

export const resetUserPassword = (domainId: string, accessToken: string, user: string, password: string) =>
  getUserApi(accessToken).resetPassword({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: user,
    passwordValue: {password},
  });
