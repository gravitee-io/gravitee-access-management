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

/** Runtime settings read from `constants.json` and `build.json` at the web root, the same files the Angular console reads. */
/**
 * Where this AM installation's Gamma lives. Present only when AM runs as part of Gamma: without it the console shows no
 * Gamma modules. Spike stand-in for the `gamma.*` settings the Management API will expose (plan Stream B).
 */
export interface GammaConfig {
    /** gamma-console root, e.g. `https://gamma.example.com`. */
    readonly consoleUrl: string;
    /** Gamma REST root that lists the modules, e.g. `/gamma`. */
    readonly apiUrl: string;
    readonly organizationId: string;
    /** The Gamma environment the module links open in. Cloud owns the AM-to-Gamma environment mapping. */
    readonly environmentHrid: string;
}

export interface AppConfig {
    /** Management API root, e.g. `http://localhost:8093/management`. */
    readonly baseURL: string;
    readonly version?: string;
    readonly gamma?: GammaConfig;
}

let config: AppConfig | undefined;

async function fetchJson(file: string): Promise<Record<string, unknown>> {
    const response = await fetch(new URL(file, document.baseURI));
    return response.ok ? response.json() : {};
}

export async function loadConfig(): Promise<AppConfig> {
    const [constants, build] = await Promise.all([fetchJson('constants.json'), fetchJson('build.json')]);
    config = { ...build, ...constants } as unknown as AppConfig;
    return config;
}

export function appConfig(): AppConfig {
    if (!config) {
        throw new Error('appConfig() called before loadConfig()');
    }
    return config;
}
