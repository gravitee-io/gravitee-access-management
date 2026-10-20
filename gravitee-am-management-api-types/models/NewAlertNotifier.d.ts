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
 * @interface NewAlertNotifier
 */
export interface NewAlertNotifier {
    /**
     *
     * @type {string}
     * @memberof NewAlertNotifier
     */
    configuration: string;
    /**
     *
     * @type {boolean}
     * @memberof NewAlertNotifier
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewAlertNotifier
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof NewAlertNotifier
     */
    type: string;
}
/**
 * Check if a given object implements the NewAlertNotifier interface.
 */
export declare function instanceOfNewAlertNotifier(value: object): value is NewAlertNotifier;
export declare function NewAlertNotifierFromJSON(json: any): NewAlertNotifier;
export declare function NewAlertNotifierFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewAlertNotifier;
export declare function NewAlertNotifierToJSON(json: any): NewAlertNotifier;
export declare function NewAlertNotifierToJSONTyped(value?: NewAlertNotifier | null, ignoreDiscriminator?: boolean): any;
