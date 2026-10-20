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
 * @interface UpdateAuthenticationDeviceNotifier
 */
export interface UpdateAuthenticationDeviceNotifier {
    /**
     *
     * @type {string}
     * @memberof UpdateAuthenticationDeviceNotifier
     */
    configuration: string;
    /**
     *
     * @type {string}
     * @memberof UpdateAuthenticationDeviceNotifier
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof UpdateAuthenticationDeviceNotifier
     */
    type: string;
}
/**
 * Check if a given object implements the UpdateAuthenticationDeviceNotifier interface.
 */
export declare function instanceOfUpdateAuthenticationDeviceNotifier(value: object): value is UpdateAuthenticationDeviceNotifier;
export declare function UpdateAuthenticationDeviceNotifierFromJSON(json: any): UpdateAuthenticationDeviceNotifier;
export declare function UpdateAuthenticationDeviceNotifierFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateAuthenticationDeviceNotifier;
export declare function UpdateAuthenticationDeviceNotifierToJSON(json: any): UpdateAuthenticationDeviceNotifier;
export declare function UpdateAuthenticationDeviceNotifierToJSONTyped(value?: UpdateAuthenticationDeviceNotifier | null, ignoreDiscriminator?: boolean): any;
