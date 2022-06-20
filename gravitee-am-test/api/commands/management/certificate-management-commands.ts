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

import {getCertificateApi} from "./service/utils";

export const createCertificate = (domainId, accessToken, certificate) =>
    getCertificateApi(accessToken).createCertificate({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        certificate: certificate
    });

export const getCertificate = (domainId, accessToken, certificateId) =>
    getCertificateApi(accessToken).findCertificate({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        certificate: certificateId
    })

export const getPublicKey = (domainId, accessToken, certificateId) =>
    getCertificateApi(accessToken).getCertificatePublicKey({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        certificate: certificateId
    })

export const getPublicKeys = (domainId, accessToken, certificateId) =>
    getCertificateApi(accessToken).getCertificatePublicKeys({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        certificate: certificateId
    })

export const getAllCertificates = (domainId, accessToken) =>
    getCertificateApi(accessToken).listCertificates({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId
    })

export const updateCertificate = (domainId, accessToken, body, certificateId) =>
    getCertificateApi(accessToken).updateCertificate({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        certificate: certificateId,
        certificate2: body
    })

export const deleteCertificate = (domainId, accessToken, certificateId) =>
    getCertificateApi(accessToken).deleteCertificate({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: domainId,
        certificate: certificateId,
    })
