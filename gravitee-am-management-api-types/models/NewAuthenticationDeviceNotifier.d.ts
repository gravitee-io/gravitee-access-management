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
 * @interface NewAuthenticationDeviceNotifier
 */
export interface NewAuthenticationDeviceNotifier {
    /**
     *
     * @type {string}
     * @memberof NewAuthenticationDeviceNotifier
     */
    configuration: string;
    /**
     *
     * @type {string}
     * @memberof NewAuthenticationDeviceNotifier
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof NewAuthenticationDeviceNotifier
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof NewAuthenticationDeviceNotifier
     */
    type: string;
}
/**
 * Check if a given object implements the NewAuthenticationDeviceNotifier interface.
 */
export declare function instanceOfNewAuthenticationDeviceNotifier(value: object): value is NewAuthenticationDeviceNotifier;
export declare function NewAuthenticationDeviceNotifierFromJSON(json: any): NewAuthenticationDeviceNotifier;
export declare function NewAuthenticationDeviceNotifierFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewAuthenticationDeviceNotifier;
export declare function NewAuthenticationDeviceNotifierToJSON(json: any): NewAuthenticationDeviceNotifier;
export declare function NewAuthenticationDeviceNotifierToJSONTyped(value?: NewAuthenticationDeviceNotifier | null, ignoreDiscriminator?: boolean): any;
