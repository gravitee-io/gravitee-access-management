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
 * @interface AccessPolicyListItem
 */
export interface AccessPolicyListItem {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof AccessPolicyListItem
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof AccessPolicyListItem
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof AccessPolicyListItem
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof AccessPolicyListItem
     */
    name?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof AccessPolicyListItem
     */
    updatedAt?: number;
}
/**
 * Check if a given object implements the AccessPolicyListItem interface.
 */
export declare function instanceOfAccessPolicyListItem(value: object): value is AccessPolicyListItem;
export declare function AccessPolicyListItemFromJSON(json: any): AccessPolicyListItem;
export declare function AccessPolicyListItemFromJSONTyped(json: any, ignoreDiscriminator: boolean): AccessPolicyListItem;
export declare function AccessPolicyListItemToJSON(json: any): AccessPolicyListItem;
export declare function AccessPolicyListItemToJSONTyped(value?: AccessPolicyListItem | null, ignoreDiscriminator?: boolean): any;
