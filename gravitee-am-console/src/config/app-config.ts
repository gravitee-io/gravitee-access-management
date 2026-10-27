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

export interface AppConfig {
    /** Management API root, e.g. `/management`. */
    readonly baseURL: string;
    readonly version?: string;
}

let config: AppConfig | undefined;

async function fetchJson(file: string): Promise<Record<string, unknown>> {
    const response = await fetch(new URL(file, document.baseURI));
    if (!response.ok) {
        throw new Error(`Could not load ${file}: HTTP ${response.status}`);
    }
    return response.json();
}

export async function loadConfig(): Promise<AppConfig> {
    const [constants, build] = await Promise.all([fetchJson('constants.json'), fetchJson('build.json')]);
    const { baseURL, version } = { ...build, ...constants };
    if (typeof baseURL !== 'string') {
        throw new Error('constants.json has no baseURL');
    }
    config = { baseURL, ...(typeof version === 'string' && { version }) };
    return config;
}

export function appConfig(): AppConfig {
    if (!config) {
        throw new Error('appConfig() called before loadConfig()');
    }
    return config;
}
