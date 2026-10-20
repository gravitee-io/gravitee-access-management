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
 * @interface SAMLAssertionAttribute
 */
export interface SAMLAssertionAttribute {
    /**
     *
     * @type {string}
     * @memberof SAMLAssertionAttribute
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof SAMLAssertionAttribute
     */
    value: string;
}
/**
 * Check if a given object implements the SAMLAssertionAttribute interface.
 */
export declare function instanceOfSAMLAssertionAttribute(value: object): value is SAMLAssertionAttribute;
export declare function SAMLAssertionAttributeFromJSON(json: any): SAMLAssertionAttribute;
export declare function SAMLAssertionAttributeFromJSONTyped(json: any, ignoreDiscriminator: boolean): SAMLAssertionAttribute;
export declare function SAMLAssertionAttributeToJSON(json: any): SAMLAssertionAttribute;
export declare function SAMLAssertionAttributeToJSONTyped(value?: SAMLAssertionAttribute | null, ignoreDiscriminator?: boolean): any;
