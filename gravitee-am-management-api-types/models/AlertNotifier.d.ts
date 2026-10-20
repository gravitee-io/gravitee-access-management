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
 * @interface AlertNotifier
 */
export interface AlertNotifier {
    /**
     *
     * @type {string}
     * @memberof AlertNotifier
     */
    configuration?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof AlertNotifier
     */
    createdAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof AlertNotifier
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof AlertNotifier
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof AlertNotifier
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof AlertNotifier
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof AlertNotifier
     */
    referenceType?: AlertNotifierReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof AlertNotifier
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof AlertNotifier
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const AlertNotifierReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type AlertNotifierReferenceTypeEnum = typeof AlertNotifierReferenceTypeEnum[keyof typeof AlertNotifierReferenceTypeEnum];
/**
 * Check if a given object implements the AlertNotifier interface.
 */
export declare function instanceOfAlertNotifier(value: object): value is AlertNotifier;
export declare function AlertNotifierFromJSON(json: any): AlertNotifier;
export declare function AlertNotifierFromJSONTyped(json: any, ignoreDiscriminator: boolean): AlertNotifier;
export declare function AlertNotifierToJSON(json: any): AlertNotifier;
export declare function AlertNotifierToJSONTyped(value?: AlertNotifier | null, ignoreDiscriminator?: boolean): any;
