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
 * @interface PasswordValue
 */
export interface PasswordValue {
    /**
     *
     * @type {string}
     * @memberof PasswordValue
     */
    password: string;
}
/**
 * Check if a given object implements the PasswordValue interface.
 */
export declare function instanceOfPasswordValue(value: object): value is PasswordValue;
export declare function PasswordValueFromJSON(json: any): PasswordValue;
export declare function PasswordValueFromJSONTyped(json: any, ignoreDiscriminator: boolean): PasswordValue;
export declare function PasswordValueToJSON(json: any): PasswordValue;
export declare function PasswordValueToJSONTyped(value?: PasswordValue | null, ignoreDiscriminator?: boolean): any;
