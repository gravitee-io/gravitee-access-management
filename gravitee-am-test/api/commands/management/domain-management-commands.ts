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
import {getDomainManagerUrl} from "./utils";

const request = require('supertest');

export const createDomain = (accessToken, name, description) =>
    request(getDomainManagerUrl(null))
        .post('')
        .set('Authorization', 'Bearer ' + accessToken)
        .send({
            name: name,
            description: description
        })
        .expect(201);

export const startDomain = (domainId, accessToken) =>
    patchDomain(domainId, accessToken, {enabled: true});

export const deleteDomain = (domainId, accessToken) =>
    request(getDomainManagerUrl(domainId))
        .delete('')
        .set('Authorization', 'Bearer ' + accessToken)
        .expect(204);

export const getDomain = (domainId, accessToken) =>
    request(getDomainManagerUrl(domainId))
        .get('')
        .set('Authorization', 'Bearer ' + accessToken)
        .expect(200);

export const patchDomain = (domainId, accessToken, body) =>
    request(getDomainManagerUrl(null))
        .patch('')
        .set('Authorization', 'Bearer ' + accessToken)
        .send(body)
        .expect(200);

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
