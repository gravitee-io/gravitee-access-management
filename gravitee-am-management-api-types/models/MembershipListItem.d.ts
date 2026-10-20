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
import type { Membership } from './Membership';
/**
 *
 * @export
 * @interface MembershipListItem
 */
export interface MembershipListItem {
    /**
     *
     * @type {Array<Membership>}
     * @memberof MembershipListItem
     */
    memberships?: Array<Membership>;
    /**
     *
     * @type {{ [key: string]: { [key: string]: any; }; }}
     * @memberof MembershipListItem
     */
    metadata?: {
        [key: string]: {
            [key: string]: any;
        };
    };
}
/**
 * Check if a given object implements the MembershipListItem interface.
 */
export declare function instanceOfMembershipListItem(value: object): value is MembershipListItem;
export declare function MembershipListItemFromJSON(json: any): MembershipListItem;
export declare function MembershipListItemFromJSONTyped(json: any, ignoreDiscriminator: boolean): MembershipListItem;
export declare function MembershipListItemToJSON(json: any): MembershipListItem;
export declare function MembershipListItemToJSONTyped(value?: MembershipListItem | null, ignoreDiscriminator?: boolean): any;
