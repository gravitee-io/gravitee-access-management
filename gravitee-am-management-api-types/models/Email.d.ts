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
 * @interface Email
 */
export interface Email {
    /**
     *
     * @type {string}
     * @memberof Email
     */
    client?: string;
    /**
     *
     * @type {string}
     * @memberof Email
     */
    content?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Email
     */
    createdAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof Email
     */
    defaultTemplate?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof Email
     */
    enabled?: boolean;
    /**
     *
     * @type {number}
     * @memberof Email
     */
    expiresAfter?: number;
    /**
     *
     * @type {string}
     * @memberof Email
     */
    from?: string;
    /**
     *
     * @type {string}
     * @memberof Email
     */
    fromName?: string;
    /**
     *
     * @type {string}
     * @memberof Email
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof Email
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof Email
     */
    referenceType?: EmailReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof Email
     */
    subject?: string;
    /**
     *
     * @type {string}
     * @memberof Email
     */
    template?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Email
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const EmailReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type EmailReferenceTypeEnum = typeof EmailReferenceTypeEnum[keyof typeof EmailReferenceTypeEnum];
/**
 * Check if a given object implements the Email interface.
 */
export declare function instanceOfEmail(value: object): value is Email;
export declare function EmailFromJSON(json: any): Email;
export declare function EmailFromJSONTyped(json: any, ignoreDiscriminator: boolean): Email;
export declare function EmailToJSON(json: any): Email;
export declare function EmailToJSONTyped(value?: Email | null, ignoreDiscriminator?: boolean): any;
