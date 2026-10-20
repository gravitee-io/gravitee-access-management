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
import type { ApplicationSettings } from './ApplicationSettings';
import type { UpdateProtectedResourceFeature } from './UpdateProtectedResourceFeature';
import type { ApplicationSecretSettings } from './ApplicationSecretSettings';
/**
 *
 * @export
 * @interface PatchProtectedResource
 */
export interface PatchProtectedResource {
    /**
     *
     * @type {string}
     * @memberof PatchProtectedResource
     */
    certificate?: string;
    /**
     *
     * @type {string}
     * @memberof PatchProtectedResource
     */
    description?: string;
    /**
     *
     * @type {Array<UpdateProtectedResourceFeature>}
     * @memberof PatchProtectedResource
     */
    features?: Array<UpdateProtectedResourceFeature>;
    /**
     *
     * @type {string}
     * @memberof PatchProtectedResource
     */
    name?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchProtectedResource
     */
    resourceIdentifiers?: Array<string>;
    /**
     *
     * @type {Array<ApplicationSecretSettings>}
     * @memberof PatchProtectedResource
     */
    secretSettings?: Array<ApplicationSecretSettings>;
    /**
     *
     * @type {ApplicationSettings}
     * @memberof PatchProtectedResource
     */
    settings?: ApplicationSettings;
}
/**
 * Check if a given object implements the PatchProtectedResource interface.
 */
export declare function instanceOfPatchProtectedResource(value: object): value is PatchProtectedResource;
export declare function PatchProtectedResourceFromJSON(json: any): PatchProtectedResource;
export declare function PatchProtectedResourceFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchProtectedResource;
export declare function PatchProtectedResourceToJSON(json: any): PatchProtectedResource;
export declare function PatchProtectedResourceToJSONTyped(value?: PatchProtectedResource | null, ignoreDiscriminator?: boolean): any;
