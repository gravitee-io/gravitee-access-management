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
 * @interface UpdateBotDetection
 */
export interface UpdateBotDetection {
    /**
     *
     * @type {string}
     * @memberof UpdateBotDetection
     */
    configuration: string;
    /**
     *
     * @type {string}
     * @memberof UpdateBotDetection
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof UpdateBotDetection
     */
    type: string;
}
/**
 * Check if a given object implements the UpdateBotDetection interface.
 */
export declare function instanceOfUpdateBotDetection(value: object): value is UpdateBotDetection;
export declare function UpdateBotDetectionFromJSON(json: any): UpdateBotDetection;
export declare function UpdateBotDetectionFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateBotDetection;
export declare function UpdateBotDetectionToJSON(json: any): UpdateBotDetection;
export declare function UpdateBotDetectionToJSONTyped(value?: UpdateBotDetection | null, ignoreDiscriminator?: boolean): any;
