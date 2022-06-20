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
import {getScopeApi} from "./service/utils";
import {expect} from "@jest/globals";

export const createScope = (domainId, accessToken, scope) =>
    getScopeApi(accessToken).createScope({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        scope: scope
    })

export const getScope = (domainId, accessToken, scopeId: string) =>
    getScopeApi(accessToken).findScope({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        scope: scopeId
    })

export const getAllScopes = (domainId, accessToken) =>
    getScopesPage(domainId, accessToken)

export const getScopesPage = (domainId, accessToken, page: number = null, size: number = null) => {
    const params = {
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
    };
    if (page !== null && size != null) {
        return getScopeApi(accessToken).listScopes({...params, page: page, size: size});
    }
    return getScopeApi(accessToken).listScopes(params);
}

export const updateScope = (domainId, accessToken, scopeId, payload) =>
    getScopeApi(accessToken).updateScope({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        scope: scopeId,
        scope2: payload
    })

export const patchScope = (domainId, accessToken, scopeId, payload) =>
    getScopeApi(accessToken).patchScope({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        scope: scopeId,
        scope2: payload
    })

export const deleteScope = (domainId, accessToken, scopeId) =>
    getScopeApi(accessToken).deleteScope({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        scope: scopeId,
    })
