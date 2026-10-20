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
 * @interface Theme
 */
export interface Theme {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Theme
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof Theme
     */
    css?: string;
    /**
     *
     * @type {string}
     * @memberof Theme
     */
    faviconUrl?: string;
    /**
     *
     * @type {string}
     * @memberof Theme
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof Theme
     */
    logoUrl?: string;
    /**
     *
     * @type {number}
     * @memberof Theme
     */
    logoWidth?: number;
    /**
     *
     * @type {string}
     * @memberof Theme
     */
    primaryButtonColorHex?: string;
    /**
     *
     * @type {string}
     * @memberof Theme
     */
    primaryTextColorHex?: string;
    /**
     *
     * @type {string}
     * @memberof Theme
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof Theme
     */
    referenceType?: ThemeReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof Theme
     */
    secondaryButtonColorHex?: string;
    /**
     *
     * @type {string}
     * @memberof Theme
     */
    secondaryTextColorHex?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Theme
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const ThemeReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type ThemeReferenceTypeEnum = typeof ThemeReferenceTypeEnum[keyof typeof ThemeReferenceTypeEnum];
/**
 * Check if a given object implements the Theme interface.
 */
export declare function instanceOfTheme(value: object): value is Theme;
export declare function ThemeFromJSON(json: any): Theme;
export declare function ThemeFromJSONTyped(json: any, ignoreDiscriminator: boolean): Theme;
export declare function ThemeToJSON(json: any): Theme;
export declare function ThemeToJSONTyped(value?: Theme | null, ignoreDiscriminator?: boolean): any;
