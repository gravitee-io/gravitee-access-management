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
import type { NewProtectedResourceFeature } from './NewProtectedResourceFeature';
/**
 *
 * @export
 * @interface NewProtectedResource
 */
export interface NewProtectedResource {
    /**
     *
     * @type {string}
     * @memberof NewProtectedResource
     */
    clientId?: string;
    /**
     *
     * @type {string}
     * @memberof NewProtectedResource
     */
    clientSecret?: string;
    /**
     *
     * @type {string}
     * @memberof NewProtectedResource
     */
    description?: string;
    /**
     *
     * @type {Array<NewProtectedResourceFeature>}
     * @memberof NewProtectedResource
     */
    features?: Array<NewProtectedResourceFeature>;
    /**
     *
     * @type {string}
     * @memberof NewProtectedResource
     */
    name: string;
    /**
     *
     * @type {Array<string>}
     * @memberof NewProtectedResource
     */
    resourceIdentifiers: Array<string>;
    /**
     *
     * @type {ApplicationSettings}
     * @memberof NewProtectedResource
     */
    settings?: ApplicationSettings;
    /**
     *
     * @type {string}
     * @memberof NewProtectedResource
     */
    type: string;
}
/**
 * Check if a given object implements the NewProtectedResource interface.
 */
export declare function instanceOfNewProtectedResource(value: object): value is NewProtectedResource;
export declare function NewProtectedResourceFromJSON(json: any): NewProtectedResource;
export declare function NewProtectedResourceFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewProtectedResource;
export declare function NewProtectedResourceToJSON(json: any): NewProtectedResource;
export declare function NewProtectedResourceToJSONTyped(value?: NewProtectedResource | null, ignoreDiscriminator?: boolean): any;
