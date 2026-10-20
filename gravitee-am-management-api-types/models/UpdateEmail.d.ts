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
 * @interface UpdateEmail
 */
export interface UpdateEmail {
    /**
     *
     * @type {string}
     * @memberof UpdateEmail
     */
    content: string;
    /**
     *
     * @type {boolean}
     * @memberof UpdateEmail
     */
    enabled?: boolean;
    /**
     *
     * @type {number}
     * @memberof UpdateEmail
     */
    expiresAfter: number;
    /**
     *
     * @type {string}
     * @memberof UpdateEmail
     */
    from: string;
    /**
     *
     * @type {string}
     * @memberof UpdateEmail
     */
    fromName?: string;
    /**
     *
     * @type {string}
     * @memberof UpdateEmail
     */
    subject: string;
}
/**
 * Check if a given object implements the UpdateEmail interface.
 */
export declare function instanceOfUpdateEmail(value: object): value is UpdateEmail;
export declare function UpdateEmailFromJSON(json: any): UpdateEmail;
export declare function UpdateEmailFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateEmail;
export declare function UpdateEmailToJSON(json: any): UpdateEmail;
export declare function UpdateEmailToJSONTyped(value?: UpdateEmail | null, ignoreDiscriminator?: boolean): any;
