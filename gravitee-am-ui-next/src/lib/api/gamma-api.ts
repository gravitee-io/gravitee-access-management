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
import type { GammaConfig } from '../../config/app-config';

/** A deployed Gamma module, as `GET /gamma/organizations/{orgId}/modules` returns it. */
export interface GammaModule {
    readonly id: string;
    readonly name: string;
    /** Absent for backend-only modules, which have no place in a module switcher. */
    readonly mfManifest?: unknown;
}

/** The endpoint is public: it lists every deployed module, the same for every user. */
export async function listGammaModules(gamma: GammaConfig): Promise<GammaModule[]> {
    const response = await fetch(`${gamma.apiUrl}/organizations/${gamma.organizationId}/modules`);
    if (!response.ok) {
        throw new Error(`Gamma modules request failed with status ${response.status}`);
    }
    return response.json();
}
