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
 * @interface UserNotificationContent
 */
export interface UserNotificationContent {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UserNotificationContent
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof UserNotificationContent
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof UserNotificationContent
     */
    message?: string;
    /**
     *
     * @type {string}
     * @memberof UserNotificationContent
     */
    status?: UserNotificationContentStatusEnum;
    /**
     *
     * @type {string}
     * @memberof UserNotificationContent
     */
    title?: string;
}
/**
 * @export
 */
export declare const UserNotificationContentStatusEnum: {
    readonly Unread: "UNREAD";
    readonly Read: "READ";
};
export type UserNotificationContentStatusEnum = typeof UserNotificationContentStatusEnum[keyof typeof UserNotificationContentStatusEnum];
/**
 * Check if a given object implements the UserNotificationContent interface.
 */
export declare function instanceOfUserNotificationContent(value: object): value is UserNotificationContent;
export declare function UserNotificationContentFromJSON(json: any): UserNotificationContent;
export declare function UserNotificationContentFromJSONTyped(json: any, ignoreDiscriminator: boolean): UserNotificationContent;
export declare function UserNotificationContentToJSON(json: any): UserNotificationContent;
export declare function UserNotificationContentToJSONTyped(value?: UserNotificationContent | null, ignoreDiscriminator?: boolean): any;
