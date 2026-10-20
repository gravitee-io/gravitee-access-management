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
 * @interface NewTheme
 */
export interface NewTheme {
    /**
     *
     * @type {string}
     * @memberof NewTheme
     */
    css?: string;
    /**
     *
     * @type {string}
     * @memberof NewTheme
     */
    faviconUrl?: string;
    /**
     *
     * @type {string}
     * @memberof NewTheme
     */
    logoUrl?: string;
    /**
     *
     * @type {number}
     * @memberof NewTheme
     */
    logoWidth?: number;
    /**
     *
     * @type {string}
     * @memberof NewTheme
     */
    primaryButtonColorHex?: string;
    /**
     *
     * @type {string}
     * @memberof NewTheme
     */
    primaryTextColorHex?: string;
    /**
     *
     * @type {string}
     * @memberof NewTheme
     */
    secondaryButtonColorHex?: string;
    /**
     *
     * @type {string}
     * @memberof NewTheme
     */
    secondaryTextColorHex?: string;
}
/**
 * Check if a given object implements the NewTheme interface.
 */
export declare function instanceOfNewTheme(value: object): value is NewTheme;
export declare function NewThemeFromJSON(json: any): NewTheme;
export declare function NewThemeFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewTheme;
export declare function NewThemeToJSON(json: any): NewTheme;
export declare function NewThemeToJSONTyped(value?: NewTheme | null, ignoreDiscriminator?: boolean): any;
