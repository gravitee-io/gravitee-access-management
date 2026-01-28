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
import { getUserApi } from './service/utils';
import { CertificateCredential, NewCertificateCredential } from '../../management/models';

/**
 * Enroll a certificate credential for a user.
 */
export const enrollCertificate = (
  domainId: string,
  userId: string,
  accessToken: string,
  newCredential: NewCertificateCredential,
): Promise<CertificateCredential> =>
  getUserApi(accessToken).enrollUserCertificateCredential({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: userId,
    newCertificateCredential: newCredential,
  });

/**
 * List certificate credentials for a user.
 */
export const listCertificateCredentials = (domainId: string, userId: string, accessToken: string): Promise<CertificateCredential[]> =>
  getUserApi(accessToken).listUserCertificateCredentials({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: userId,
  });

/**
 * Get a certificate credential by ID.
 */
export const getCertificateCredential = (
  domainId: string,
  userId: string,
  credentialId: string,
  accessToken: string,
): Promise<CertificateCredential> =>
  getUserApi(accessToken).getUserCertificateCredential({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: userId,
    credential: credentialId,
  });

/**
 * Delete (revoke) a certificate credential.
 */
export const deleteCertificateCredential = (domainId: string, userId: string, credentialId: string, accessToken: string): Promise<void> =>
  getUserApi(accessToken).revokeUserCertificateCredential({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: userId,
    credential: credentialId,
  });

// Re-export types for convenience
export type { CertificateCredential } from '../../management/models';
export type { NewCertificateCredential } from '../../management/models';
