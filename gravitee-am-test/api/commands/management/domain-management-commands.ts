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

import {getDomainApi, getDomainManagerUrl} from "./service/utils";

const request = require('supertest');

export const createDomain = (accessToken, name, description) =>
    getDomainApi(accessToken).createDomain({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: {
            name: name,
            description: description
        }
    });


export const deleteDomain = (domainId, accessToken) =>
    getDomainApi(accessToken).deleteDomain({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId
    });

export const patchDomain = (domainId, accessToken, body) =>
    getDomainApi(accessToken).patchDomain({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        // domain in path param
        domain: domainId,
        // domain payload
        domain2: body
    });

export const startDomain = (domainId, accessToken) => patchDomain(domainId, accessToken, {enabled: true})

export const getDomain = (domainId, accessToken) =>
    getDomainApi(accessToken).findDomain({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        // domain in path param
        domain: domainId
    })

export const createAcceptAllDeviceNotifier = (domainId, accessToken) =>
    request(getDomainManagerUrl(null) + "/auth-device-notifiers")
        .post('')
        .set('Authorization', 'Bearer ' + accessToken)
        .send(
            {
                type: "http-am-authdevice-notifier",
                configuration: "{\"endpoint\":\"http://localhost:8080/ciba/notify/accept-all\",\"headerName\":\"Authorization\",\"connectTimeout\":5000,\"idleTimeout\":10000,\"maxPoolSize\":10}",
                name: "Always OK notifier"
            }
        )
        .expect(201);
