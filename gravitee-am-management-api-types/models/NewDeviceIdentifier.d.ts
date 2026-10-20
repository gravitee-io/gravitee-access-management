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
 * @interface NewDeviceIdentifier
 */
export interface NewDeviceIdentifier {
    /**
     *
     * @type {string}
     * @memberof NewDeviceIdentifier
     */
    configuration: string;
    /**
     *
     * @type {string}
     * @memberof NewDeviceIdentifier
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof NewDeviceIdentifier
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof NewDeviceIdentifier
     */
    type: string;
}
/**
 * Check if a given object implements the NewDeviceIdentifier interface.
 */
export declare function instanceOfNewDeviceIdentifier(value: object): value is NewDeviceIdentifier;
export declare function NewDeviceIdentifierFromJSON(json: any): NewDeviceIdentifier;
export declare function NewDeviceIdentifierFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewDeviceIdentifier;
export declare function NewDeviceIdentifierToJSON(json: any): NewDeviceIdentifier;
export declare function NewDeviceIdentifierToJSONTyped(value?: NewDeviceIdentifier | null, ignoreDiscriminator?: boolean): any;
