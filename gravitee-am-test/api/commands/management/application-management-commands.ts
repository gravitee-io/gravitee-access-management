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

import {getApplicationApi} from "./service/utils";

export const createApplication = (domainId, accessToken, body) =>
    getApplicationApi(accessToken).createApplication({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        application: body
    });

export const getApplication = (domainId, accessToken, applicationId) =>
    getApplicationApi(accessToken).findApplication({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        application: applicationId
    })

export const getAllApplications = (domainId, accessToken) => getApplicationPage(domainId, accessToken)

export const getApplicationPage = (domainId, accessToken, page: number = null, size: number = null) => {
    const params = {
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId
    };
    if (page !== null && size != null) {
        return getApplicationApi(accessToken).listApplications({...params, "page": page, "size": size});
    }
    return getApplicationApi(accessToken).listApplications(params);
}

export const patchApplication = (domainId, accessToken, body, applicationId) =>
    getApplicationApi(accessToken).patchApplication({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        application: applicationId,
        application2: body
    })

export const updateApplication = (domainId, accessToken, body, applicationId) =>
    getApplicationApi(accessToken).updateApplication({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        application: applicationId,
        application2: body
    })

export const deleteApplication = (domainId, accessToken, applicationId) =>
    getApplicationApi(accessToken).deleteApplication({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        application: applicationId,
    })

export const renewApplicationSecrets = (domainId, accessToken, applicationId) =>
    getApplicationApi(accessToken).renewClientSecret({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        application: applicationId,
    });
