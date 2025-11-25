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

import { Configuration } from '../../../management/runtime';
import { managementConf } from './config';
import { DomainApi } from '@management-apis/DomainApi';
import { ApplicationApi } from '@management-apis/ApplicationApi';
import { UserApi } from '@management-apis/UserApi';
import { RoleApi } from '@management-apis/RoleApi';
import { GroupApi } from '@management-apis/GroupApi';
import { IdentityProviderApi } from '@management-apis/IdentityProviderApi';
import { ScopeApi } from '@management-apis/ScopeApi';
import { CertificateApi } from '@management-apis/CertificateApi';
import { DictionaryApi } from '@management-apis/DictionaryApi';
import { ThemeApi } from '@management-apis/ThemeApi';
import { FormApi } from '@management-apis/FormApi';
import { FactorApi } from '@management-apis/FactorApi';
import { ResourceApi } from '@management-apis/ResourceApi';
import { DeviceIdentifiersApi } from '@management-apis/DeviceIdentifiersApi';
import { PasswordPolicyApi } from '@management-apis/PasswordPolicyApi';
import { ExtensionGrantApi } from '@management-apis/ExtensionGrantApi';
import { BotDetectionApi } from '@management-apis/BotDetectionApi';
import { ProtectedResourceApi } from '@management-apis/ProtectedResourceApi';
import { DefaultApi } from '@management-apis/DefaultApi';

function createAccessTokenConfig(accessToken) {
  return new Configuration({ ...managementConf, accessToken: accessToken });
}

export const getDomainManagerUrl = (domainId: String) => {
  const domainPathParam = domainId ? `${domainId}` : '';
  return (
    process.env.AM_MANAGEMENT_URL +
    `/management/organizations/${process.env.AM_DEF_ORG_ID}` +
    `/environments/${process.env.AM_DEF_ENV_ID}/domains/${domainPathParam}`
  );
};

export const getOrganisationManagementUrl = () => {
  return process.env.AM_MANAGEMENT_URL + `/management/organizations/${process.env.AM_DEF_ORG_ID}`;
};

export function getDomainApi(accessToken) {
  return new DomainApi(createAccessTokenConfig(accessToken));
}

export function getApplicationApi(accessToken) {
  return new ApplicationApi(createAccessTokenConfig(accessToken));
}

export function getProtectedResourcesApi(accessToken) {
  return new ProtectedResourceApi(createAccessTokenConfig(accessToken));
}

export function getRoleApi(accessToken) {
  return new RoleApi(createAccessTokenConfig(accessToken));
}

export function getUserApi(accessToken) {
  return new UserApi(createAccessTokenConfig(accessToken));
}

export function getGroupApi(accessToken) {
  return new GroupApi(createAccessTokenConfig(accessToken));
}

export function getIdpApi(accessToken) {
  return new IdentityProviderApi(createAccessTokenConfig(accessToken));
}

export function getScopeApi(accessToken) {
  return new ScopeApi(createAccessTokenConfig(accessToken));
}

export function getCertificateApi(accessToken) {
  return new CertificateApi(createAccessTokenConfig(accessToken));
}

export function getDictionaryApi(accessToken) {
  return new DictionaryApi(createAccessTokenConfig(accessToken));
}

export function getThemeApi(accessToken) {
  return new ThemeApi(createAccessTokenConfig(accessToken));
}

export function getFormApi(accessToken) {
  return new FormApi(createAccessTokenConfig(accessToken));
}

export function getFactorApi(accessToken) {
  return new FactorApi(createAccessTokenConfig(accessToken));
}

export function getPasswordPolicyApi(accessToken) {
  return new PasswordPolicyApi(createAccessTokenConfig(accessToken));
}

export function getResourceApi(accessToken) {
  return new ResourceApi(createAccessTokenConfig(accessToken));
}

export function getDeviceIdentifiersApi(accessToken) {
  return new DeviceIdentifiersApi(createAccessTokenConfig(accessToken));
}

export function getExtensionApi(accessToken) {
  return new ExtensionGrantApi(createAccessTokenConfig(accessToken));
}

export function getBotDetectionApi(accessToken) {
  return new BotDetectionApi(createAccessTokenConfig(accessToken));
}

export function getDefaultApi(accessToken) {
  return new DefaultApi(createAccessTokenConfig(accessToken));
}

export function createRandomString(length: number) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  let result = '';
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}
