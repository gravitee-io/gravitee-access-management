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
import type { Group } from './Group';
/**
 *
 * @export
 * @interface GroupPage
 */
export interface GroupPage {
    /**
     *
     * @type {number}
     * @memberof GroupPage
     */
    currentPage?: number;
    /**
     *
     * @type {Array<Group>}
     * @memberof GroupPage
     */
    data?: Array<Group>;
    /**
     *
     * @type {number}
     * @memberof GroupPage
     */
    totalCount?: number;
}
/**
 * Check if a given object implements the GroupPage interface.
 */
export declare function instanceOfGroupPage(value: object): value is GroupPage;
export declare function GroupPageFromJSON(json: any): GroupPage;
export declare function GroupPageFromJSONTyped(json: any, ignoreDiscriminator: boolean): GroupPage;
export declare function GroupPageToJSON(json: any): GroupPage;
export declare function GroupPageToJSONTyped(value?: GroupPage | null, ignoreDiscriminator?: boolean): any;
