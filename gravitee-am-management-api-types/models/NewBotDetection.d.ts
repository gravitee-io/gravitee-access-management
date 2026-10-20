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
 * @interface NewBotDetection
 */
export interface NewBotDetection {
    /**
     *
     * @type {string}
     * @memberof NewBotDetection
     */
    configuration: string;
    /**
     *
     * @type {string}
     * @memberof NewBotDetection
     */
    detectionType: string;
    /**
     *
     * @type {string}
     * @memberof NewBotDetection
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof NewBotDetection
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof NewBotDetection
     */
    type: string;
}
/**
 * Check if a given object implements the NewBotDetection interface.
 */
export declare function instanceOfNewBotDetection(value: object): value is NewBotDetection;
export declare function NewBotDetectionFromJSON(json: any): NewBotDetection;
export declare function NewBotDetectionFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewBotDetection;
export declare function NewBotDetectionToJSON(json: any): NewBotDetection;
export declare function NewBotDetectionToJSONTyped(value?: NewBotDetection | null, ignoreDiscriminator?: boolean): any;
