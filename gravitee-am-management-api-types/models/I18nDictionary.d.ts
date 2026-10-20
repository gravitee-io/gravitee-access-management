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
import type { Reference } from './Reference';
/**
 *
 * @export
 * @interface I18nDictionary
 */
export interface I18nDictionary {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof I18nDictionary
     */
    createdAt?: number;
    /**
     *
     * @type {{ [key: string]: string; }}
     * @memberof I18nDictionary
     */
    entries?: {
        [key: string]: string;
    };
    /**
     *
     * @type {string}
     * @memberof I18nDictionary
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof I18nDictionary
     */
    locale?: string;
    /**
     *
     * @type {string}
     * @memberof I18nDictionary
     */
    name?: string;
    /**
     *
     * @type {Reference}
     * @memberof I18nDictionary
     */
    reference?: Reference;
    /**
     *
     * @type {string}
     * @memberof I18nDictionary
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof I18nDictionary
     */
    referenceType?: I18nDictionaryReferenceTypeEnum;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof I18nDictionary
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const I18nDictionaryReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type I18nDictionaryReferenceTypeEnum = typeof I18nDictionaryReferenceTypeEnum[keyof typeof I18nDictionaryReferenceTypeEnum];
/**
 * Check if a given object implements the I18nDictionary interface.
 */
export declare function instanceOfI18nDictionary(value: object): value is I18nDictionary;
export declare function I18nDictionaryFromJSON(json: any): I18nDictionary;
export declare function I18nDictionaryFromJSONTyped(json: any, ignoreDiscriminator: boolean): I18nDictionary;
export declare function I18nDictionaryToJSON(json: any): I18nDictionary;
export declare function I18nDictionaryToJSONTyped(value?: I18nDictionary | null, ignoreDiscriminator?: boolean): any;
