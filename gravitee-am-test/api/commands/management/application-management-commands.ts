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

export const createApplication = (domainId, accessToken, body, status: number = 201) =>
    request(getApplicationUrl(domainId, null))
        .post('')
        .set('Authorization', 'Bearer ' + accessToken)
        .send(body)
        .expect(status);

export const getApplication = (domainId, accessToken, applicationId, status: number = 200) =>
    request(getApplicationUrl(domainId, applicationId))
        .get('')
        .set('Authorization', 'Bearer ' + accessToken)
        .expect(status);

export const getAllApplications = (domainId, accessToken, status: number = 200) =>
    getApplication(domainId, accessToken, null, status);

export const updateApplication = (domainId, accessToken, body, applicationId, status: number = 200) =>
    request(getApplicationUrl(domainId, applicationId))
        .patch('')
        .set('Authorization', 'Bearer ' + accessToken)
        .send(body)
        .expect(status);

export const deleteApplication = (domainId, accessToken, applicationId, status: number = 204) =>
    request(getApplicationUrl(domainId, applicationId))
        .delete('')
        .set('Authorization', 'Bearer ' + accessToken)
        .expect(status);

const getApplicationUrl = (domainId, applicationId) => {
    const appPath = applicationId ? `/applications/${applicationId}` : '/applications/';
    return getDomainManagerUrl(domainId) + appPath;
};
