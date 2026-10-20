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
 * @interface NewClientSecret
 */
export interface NewClientSecret {
    /**
     *
     * @type {string}
     * @memberof NewClientSecret
     */
    name: string;
}
/**
 * Check if a given object implements the NewClientSecret interface.
 */
export declare function instanceOfNewClientSecret(value: object): value is NewClientSecret;
export declare function NewClientSecretFromJSON(json: any): NewClientSecret;
export declare function NewClientSecretFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewClientSecret;
export declare function NewClientSecretToJSON(json: any): NewClientSecret;
export declare function NewClientSecretToJSONTyped(value?: NewClientSecret | null, ignoreDiscriminator?: boolean): any;
