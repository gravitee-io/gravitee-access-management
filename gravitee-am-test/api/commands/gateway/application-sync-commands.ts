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
import { Application } from '@management-models/Application';
import { retryUntil } from '@utils-commands/retry';
import { getApplicationSyncStatus } from '@gateway-commands/sync-state-commands';

const DEFAULT_DOMAIN_SYNC_TIMEOUT_MS = 30000;
const DEFAULT_DOMAIN_SYNC_INTERVAL_MS = 500;

export const syncApplication = async (
  domainId: string,
  applicationId: string,
  applicationPromise: Promise<Application>,
  options?: any,
): Promise<Application> => {
  const before = await getApplicationSyncStatus(domainId, applicationId);
  const result = await applicationPromise;
  const { timeoutMillis = DEFAULT_DOMAIN_SYNC_TIMEOUT_MS, intervalMillis = DEFAULT_DOMAIN_SYNC_INTERVAL_MS } = options || {};
  await retryUntil(
    async () => {
      try {
        const after = await getApplicationSyncStatus(domainId, applicationId);
        return {
          updatedAt: after.lastSync,
        };
      } catch (error: unknown) {
        // Log at debug level to handle the exception while avoiding log spam
        const errorMessage = error instanceof Error ? error.message : String(error);
        console.debug(`Error fetching domainState ${domainId} for sync check: ${errorMessage}`);
        return { updatedAt: null };
      }
    },
    (result) => {
      const { updatedAt } = result;

      // If updatedAt is null, domain is not ready or has no timestamp. Continue polling.
      if (updatedAt === null) {
        return false;
      }

      // Domain hasn't been updated - check if stable long enough
      return before.lastSync < updatedAt;
    },
    {
      timeoutMillis,
      intervalMillis,
      onDone: () => {
        const duration = before.lastSync ? Date.now() - (before.lastSync || 0) : 0;
        console.debug(`Domain ${domainId} sync complete (stable for ${duration}ms)`);
      },
      onRetry: () => {
        // Silent retry - avoid log spam
      },
    },
  );

  return result;
};
