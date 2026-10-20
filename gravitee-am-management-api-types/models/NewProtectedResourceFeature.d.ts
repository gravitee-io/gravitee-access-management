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
/**
 *
 * @export
 * @interface NewProtectedResourceFeature
 */
export interface NewProtectedResourceFeature {
    /**
     *
     * @type {string}
     * @memberof NewProtectedResourceFeature
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof NewProtectedResourceFeature
     */
    key: string;
    /**
     *
     * @type {string}
     * @memberof NewProtectedResourceFeature
     */
    type: string;
}
/**
 * Check if a given object implements the NewProtectedResourceFeature interface.
 */
export declare function instanceOfNewProtectedResourceFeature(value: object): value is NewProtectedResourceFeature;
export declare function NewProtectedResourceFeatureFromJSON(json: any): NewProtectedResourceFeature;
export declare function NewProtectedResourceFeatureFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewProtectedResourceFeature;
export declare function NewProtectedResourceFeatureToJSON(json: any): NewProtectedResourceFeature;
export declare function NewProtectedResourceFeatureToJSONTyped(value?: NewProtectedResourceFeature | null, ignoreDiscriminator?: boolean): any;
