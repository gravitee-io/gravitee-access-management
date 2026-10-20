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
 * @interface ProtectedResourceFeature
 */
export interface ProtectedResourceFeature {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ProtectedResourceFeature
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof ProtectedResourceFeature
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof ProtectedResourceFeature
     */
    key?: string;
    /**
     *
     * @type {string}
     * @memberof ProtectedResourceFeature
     */
    type?: ProtectedResourceFeatureTypeEnum;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ProtectedResourceFeature
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const ProtectedResourceFeatureTypeEnum: {
    readonly McpTool: "MCP_TOOL";
};
export type ProtectedResourceFeatureTypeEnum = typeof ProtectedResourceFeatureTypeEnum[keyof typeof ProtectedResourceFeatureTypeEnum];
/**
 * Check if a given object implements the ProtectedResourceFeature interface.
 */
export declare function instanceOfProtectedResourceFeature(value: object): value is ProtectedResourceFeature;
export declare function ProtectedResourceFeatureFromJSON(json: any): ProtectedResourceFeature;
export declare function ProtectedResourceFeatureFromJSONTyped(json: any, ignoreDiscriminator: boolean): ProtectedResourceFeature;
export declare function ProtectedResourceFeatureToJSON(json: any): ProtectedResourceFeature;
export declare function ProtectedResourceFeatureToJSONTyped(value?: ProtectedResourceFeature | null, ignoreDiscriminator?: boolean): any;
