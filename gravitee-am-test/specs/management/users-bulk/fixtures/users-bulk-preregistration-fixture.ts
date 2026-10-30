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
import { expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { bulkCreateUsers, getUser } from '@management-commands/user-management-commands';
import { clearEmails } from '@utils-commands/email-commands';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { BulkOperationResultObject } from '@management-models/BulkOperationResultObject';

export type Candidate = { username: string; email: string; preRegistration: boolean };

export interface UsersBulkFixture {
  domain: Domain;
  accessToken: string;
  candidate: (preRegistration: boolean) => Candidate;
  bulkCreate: (candidates: Candidate[]) => Promise<BulkOperationResultObject[]>;
  readUser: (userId: string) => Promise<any>;
  cleanup: () => Promise<void>;
}

export const setupUsersBulkFixture = async (): Promise<UsersBulkFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    domain = (await setupDomainForTest(uniqueName('users-bulk-prereg', true), { accessToken, waitForStart: true })).domain;

    const candidate = (preRegistration: boolean): Candidate => {
      const username = uniqueName('prereg', true);
      return { username, email: `${username}@user.com`, preRegistration };
    };

    const bulkCreate = async (candidates: Candidate[]) => {
      await Promise.all(candidates.map((c) => clearEmails(c.email)));
      const response = await bulkCreateUsers(
        domain.id,
        accessToken,
        candidates.map((c) => ({
          username: c.username,
          email: c.email,
          firstName: 'Bulk',
          lastName: c.username,
          preRegistration: c.preRegistration,
          password: c.preRegistration ? undefined : 'SomeP@ssw0rd01',
        })),
      );
      expect(response.allSuccessful).toBe(true);
      expect(response.results).toHaveLength(candidates.length);
      response.results.forEach((result) => expect(result.httpStatus).toBe(201));
      return response.results;
    };

    const cleanup = async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    };

    return {
      domain,
      accessToken,
      candidate,
      bulkCreate,
      readUser: (userId: string) => getUser(domain.id, accessToken, userId),
      cleanup,
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
