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

/** A resource server the security domain exposes, flattened across its Cross App Access trusted domains. */
export interface CrossAppAccessResourceServerOption {
  trustDomainId: string;
  trustDomainName: string;
  resourceServerId: string;
  name: string;
  resource: string;
}

/** One row of the application's resource server table: which resource server, and the identity held there. */
export interface CrossAppAccessResourceServerMapping {
  trustDomainId?: string;
  resourceServerId: string;
  clientId: string;
}

/** What the application stores. Absent reads the same as disabled. */
export interface ApplicationCrossAppAccessSettings {
  enabled?: boolean;
  resourceServers?: CrossAppAccessResourceServerMapping[];
}

/** A table row joined with the resource server it points at, unresolved when that resource server is gone. */
export interface CrossAppAccessRow {
  mapping: CrossAppAccessResourceServerMapping;
  option?: CrossAppAccessResourceServerOption;
}

export const DEFAULT_ID_JAG_VALIDITY_SECONDS = 300;

export const MIN_ID_JAG_VALIDITY_SECONDS = 1;

export function resourceServerLabel(option: CrossAppAccessResourceServerOption): string {
  return `${option.trustDomainName} — ${option.name} (${option.resource})`;
}
