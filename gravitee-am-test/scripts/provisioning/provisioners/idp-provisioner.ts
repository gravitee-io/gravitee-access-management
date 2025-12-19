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
import { createMongoIdp } from '@utils-commands/idps-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';

export async function ensureIdp(domainId: string, accessToken: string, kind?: string) {
  if (kind === 'mongo') {
    const idp = await createMongoIdp(domainId, accessToken);
    return { idp: idp?.id || 'mongo' };
  }
  if (!kind || kind === 'default') {
    // If default, try to find an existing IDP (e.g. inline-idp created by default)
    const idps = await getAllIdps(domainId, accessToken);
    if (idps && idps.length > 0) {
      return { idp: idps[0].id };
    }
    return { idp: undefined };
  }
  console.warn(`Unsupported idp kind "${kind}", skipping`);
  return { idp: undefined };
}
