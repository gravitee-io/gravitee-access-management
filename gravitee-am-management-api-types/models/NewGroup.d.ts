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
 * @interface NewGroup
 */
export interface NewGroup {
    /**
     *
     * @type {string}
     * @memberof NewGroup
     */
    description?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof NewGroup
     */
    members?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof NewGroup
     */
    name: string;
}
/**
 * Check if a given object implements the NewGroup interface.
 */
export declare function instanceOfNewGroup(value: object): value is NewGroup;
export declare function NewGroupFromJSON(json: any): NewGroup;
export declare function NewGroupFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewGroup;
export declare function NewGroupToJSON(json: any): NewGroup;
export declare function NewGroupToJSONTyped(value?: NewGroup | null, ignoreDiscriminator?: boolean): any;
