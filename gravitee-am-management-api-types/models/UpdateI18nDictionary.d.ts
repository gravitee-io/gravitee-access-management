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
 * @interface UpdateI18nDictionary
 */
export interface UpdateI18nDictionary {
    /**
     *
     * @type {{ [key: string]: string; }}
     * @memberof UpdateI18nDictionary
     */
    entries?: {
        [key: string]: string;
    };
    /**
     *
     * @type {string}
     * @memberof UpdateI18nDictionary
     */
    locale?: string;
    /**
     *
     * @type {string}
     * @memberof UpdateI18nDictionary
     */
    name?: string;
}
/**
 * Check if a given object implements the UpdateI18nDictionary interface.
 */
export declare function instanceOfUpdateI18nDictionary(value: object): value is UpdateI18nDictionary;
export declare function UpdateI18nDictionaryFromJSON(json: any): UpdateI18nDictionary;
export declare function UpdateI18nDictionaryFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateI18nDictionary;
export declare function UpdateI18nDictionaryToJSON(json: any): UpdateI18nDictionary;
export declare function UpdateI18nDictionaryToJSONTyped(value?: UpdateI18nDictionary | null, ignoreDiscriminator?: boolean): any;
