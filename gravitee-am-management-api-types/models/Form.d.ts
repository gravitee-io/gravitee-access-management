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
 * @interface Form
 */
export interface Form {
    /**
     *
     * @type {string}
     * @memberof Form
     */
    assets?: string;
    /**
     *
     * @type {string}
     * @memberof Form
     */
    client?: string;
    /**
     *
     * @type {string}
     * @memberof Form
     */
    content?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Form
     */
    createdAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof Form
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof Form
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof Form
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof Form
     */
    referenceType?: FormReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof Form
     */
    template?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Form
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const FormReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type FormReferenceTypeEnum = typeof FormReferenceTypeEnum[keyof typeof FormReferenceTypeEnum];
/**
 * Check if a given object implements the Form interface.
 */
export declare function instanceOfForm(value: object): value is Form;
export declare function FormFromJSON(json: any): Form;
export declare function FormFromJSONTyped(json: any, ignoreDiscriminator: boolean): Form;
export declare function FormToJSON(json: any): Form;
export declare function FormToJSONTyped(value?: Form | null, ignoreDiscriminator?: boolean): any;
