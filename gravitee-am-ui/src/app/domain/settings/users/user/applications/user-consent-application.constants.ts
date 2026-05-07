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

/** Matches {@link io.gravitee.am.management.handlers.management.api.adapter.ScopeApprovalAdapterImpl#UNKNOWN_ID}. */
export const USER_CONSENT_UNKNOWN_APPLICATION_ENTITY_ID = 'unknown-id';

/** Matches {@link io.gravitee.am.management.handlers.management.api.adapter.ConsentApplicationEntityFactory#CIMD_CLIENT}. */
export const USER_CONSENT_CIMD_CLIENT_ENTITY_ID = 'cimd-client';

export function isUserConsentSyntheticApplicationId(appId: string | null | undefined): boolean {
  return appId === USER_CONSENT_UNKNOWN_APPLICATION_ENTITY_ID || appId === USER_CONSENT_CIMD_CLIENT_ENTITY_ID;
}
