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
 * @interface ThemeEntity
 */
export interface ThemeEntity {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ThemeEntity
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof ThemeEntity
     */
    css?: string;
    /**
     *
     * @type {string}
     * @memberof ThemeEntity
     */
    faviconUrl?: string;
    /**
     *
     * @type {string}
     * @memberof ThemeEntity
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof ThemeEntity
     */
    logoUrl?: string;
    /**
     *
     * @type {number}
     * @memberof ThemeEntity
     */
    logoWidth?: number;
    /**
     *
     * @type {string}
     * @memberof ThemeEntity
     */
    primaryButtonColorHex?: string;
    /**
     *
     * @type {string}
     * @memberof ThemeEntity
     */
    primaryTextColorHex?: string;
    /**
     *
     * @type {string}
     * @memberof ThemeEntity
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof ThemeEntity
     */
    referenceType?: ThemeEntityReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof ThemeEntity
     */
    secondaryButtonColorHex?: string;
    /**
     *
     * @type {string}
     * @memberof ThemeEntity
     */
    secondaryTextColorHex?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ThemeEntity
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const ThemeEntityReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type ThemeEntityReferenceTypeEnum = typeof ThemeEntityReferenceTypeEnum[keyof typeof ThemeEntityReferenceTypeEnum];
/**
 * Check if a given object implements the ThemeEntity interface.
 */
export declare function instanceOfThemeEntity(value: object): value is ThemeEntity;
export declare function ThemeEntityFromJSON(json: any): ThemeEntity;
export declare function ThemeEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): ThemeEntity;
export declare function ThemeEntityToJSON(json: any): ThemeEntity;
export declare function ThemeEntityToJSONTyped(value?: ThemeEntity | null, ignoreDiscriminator?: boolean): any;
