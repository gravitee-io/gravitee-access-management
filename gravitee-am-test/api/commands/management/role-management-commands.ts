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

import {getRoleApi} from "./service/utils";

export const createRole = (domainId, accessToken, role) =>
    getRoleApi(accessToken).createRole({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        role: role
    })

export const getRole = (domainId, accessToken, roleId) =>
    getRoleApi(accessToken).findRole({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        role: roleId
    })


export const getRolePage = (domainId, accessToken, page: number = null, size: number = null) => {
    let params = {
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
    };
    if (page !== null && size != null) {
        return getRoleApi(accessToken).findRoles({...params, page: page, size: size});
    }
    return getRoleApi(accessToken).findRoles(params);
}

export const getAllRoles = (domainId, accessToken) => getRolePage(domainId, accessToken)

export const updateRole = (domainId, accessToken, roleId, payload) =>
    getRoleApi(accessToken).updateRole({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        // role path param
        role: roleId,
        //role payload
        role2: payload
    })

export const deleteRole = (domainId, accessToken, roleId) =>
    getRoleApi(accessToken).deleteRole({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        role: roleId,
    })
