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
 * @interface BotDetection
 */
export interface BotDetection {
    /**
     *
     * @type {string}
     * @memberof BotDetection
     */
    configuration?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof BotDetection
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof BotDetection
     */
    detectionType?: string;
    /**
     *
     * @type {string}
     * @memberof BotDetection
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof BotDetection
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof BotDetection
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof BotDetection
     */
    referenceType?: BotDetectionReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof BotDetection
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof BotDetection
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const BotDetectionReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type BotDetectionReferenceTypeEnum = typeof BotDetectionReferenceTypeEnum[keyof typeof BotDetectionReferenceTypeEnum];
/**
 * Check if a given object implements the BotDetection interface.
 */
export declare function instanceOfBotDetection(value: object): value is BotDetection;
export declare function BotDetectionFromJSON(json: any): BotDetection;
export declare function BotDetectionFromJSONTyped(json: any, ignoreDiscriminator: boolean): BotDetection;
export declare function BotDetectionToJSON(json: any): BotDetection;
export declare function BotDetectionToJSONTyped(value?: BotDetection | null, ignoreDiscriminator?: boolean): any;
