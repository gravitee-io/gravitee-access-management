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

import {Configuration} from "../../../management/runtime";
import {managementConf} from "./config";
import {DomainApi} from "@management-apis/DomainApi";
import {ApplicationApi} from "@management-apis/ApplicationApi";
import {UserApi} from "@management-apis/UserApi";
import {RoleApi} from "@management-apis/RoleApi";
import {GroupApi} from "@management-apis/GroupApi";
import {IdentityProviderApi} from "@management-apis/IdentityProviderApi";
import {ScopeApi} from "@management-apis/ScopeApi";
import {CertificateApi} from "@management-apis/CertificateApi";
import {DictionaryApi} from "@management-apis/DictionaryApi";

function createAccessTokenConfig(accessToken) {
    return new Configuration({...managementConf, apiKey: 'Bearer ' + accessToken});
}

export const getDomainManagerUrl = (domainId: String) => {
    const domainPathParam = domainId ? `${domainId}` : '';
    return process.env.AM_MANAGEMENT_URL
        + `/management/organizations/${process.env.AM_DEF_ORG_ID}`
        + `/environments/${process.env.AM_DEF_ENV_ID}/domains/${domainPathParam}`;
};

export function getDomainApi(accessToken) {
    return new DomainApi(createAccessTokenConfig(accessToken));
}

export function getApplicationApi(accessToken) {
    return new ApplicationApi(createAccessTokenConfig(accessToken));
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