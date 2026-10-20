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
 * @interface UpdateGroup
 */
export interface UpdateGroup {
    /**
     *
     * @type {string}
     * @memberof UpdateGroup
     */
    description?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof UpdateGroup
     */
    members?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof UpdateGroup
     */
    name: string;
    /**
     *
     * @type {Array<string>}
     * @memberof UpdateGroup
     */
    roles?: Array<string>;
}
/**
 * Check if a given object implements the UpdateGroup interface.
 */
export declare function instanceOfUpdateGroup(value: object): value is UpdateGroup;
export declare function UpdateGroupFromJSON(json: any): UpdateGroup;
export declare function UpdateGroupFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateGroup;
export declare function UpdateGroupToJSON(json: any): UpdateGroup;
export declare function UpdateGroupToJSONTyped(value?: UpdateGroup | null, ignoreDiscriminator?: boolean): any;
