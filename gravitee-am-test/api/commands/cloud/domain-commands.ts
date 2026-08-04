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

import { getDomainApi } from '@management-commands/service/utils';

/** Organization / environment a domain was created in — never the DEFAULT-scoped helpers. */
export interface CloudDomainScope {
  accessToken: string;
  organizationId: string;
  environmentId: string;
}

/**
 * Best-effort domain delete for managed-cloud fixtures. Never throws — cleanup must keep going.
 * Prefer {@link bindSafeDeleteCloudDomain} so a fixture closes over its org/env once.
 */
export const safeDeleteCloudDomain = async (
  scope: CloudDomainScope,
  domainId: string | null | undefined,
): Promise<void> => {
  if (!domainId) {
    return;
  }
  try {
    await getDomainApi(scope.accessToken).deleteDomain({
      organizationId: scope.organizationId,
      environmentId: scope.environmentId,
      domain: domainId,
    });
  } catch (err: any) {
    console.warn(`cleanup: failed to delete domain ${domainId}: ${err.message}`);
  }
};

/** Delete several domains in the same org/env, in parallel. */
export const safeDeleteCloudDomains = (scope: CloudDomainScope, domainIds: Array<string | null | undefined>): Promise<void> =>
  Promise.all(domainIds.map((id) => safeDeleteCloudDomain(scope, id))).then(() => undefined);

/**
 * Bind domain cleanup to a fixture's org/env so callers only pass the domain id.
 * Use from cloud fixtures that create domains in a known environment.
 */
export const bindSafeDeleteCloudDomain = (scope: CloudDomainScope) => (domainId: string | null | undefined) =>
  safeDeleteCloudDomain(scope, domainId);
