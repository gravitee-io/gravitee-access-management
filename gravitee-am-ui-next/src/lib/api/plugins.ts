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
/** A plugin of the platform catalogue, as `GET /platform/plugins/*` returns it. */
export interface Plugin {
    readonly id: string;
    readonly name: string;
    readonly description?: string;
    readonly version?: string;
    /** The licence feature the plugin needs. Absent or blank for an OSS plugin. */
    readonly feature?: string;
}

/** The identity provider plugins also return these fields when the request expands them. */
export interface IdentityProviderPlugin extends Plugin {
    readonly displayName?: string;
    /** A data URI. */
    readonly icon?: string;
    readonly labels?: readonly string[];
    /** Set by the console for the social and enterprise plugins. */
    readonly external: boolean;
}
