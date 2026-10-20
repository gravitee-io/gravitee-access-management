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
 * A single field shown on a user-facing form, such as registration.
 * @export
 * @interface FormField
 */
export interface FormField {
    /**
     * Identifier of the field, mapped to a user attribute.
     * @type {string}
     * @memberof FormField
     */
    key?: string;
    /**
     * Label displayed for the field.
     * @type {string}
     * @memberof FormField
     */
    label?: string;
    /**
     * Input type of the field.
     * @type {string}
     * @memberof FormField
     */
    type?: string;
}
/**
 * Check if a given object implements the FormField interface.
 */
export declare function instanceOfFormField(value: object): value is FormField;
export declare function FormFieldFromJSON(json: any): FormField;
export declare function FormFieldFromJSONTyped(json: any, ignoreDiscriminator: boolean): FormField;
export declare function FormFieldToJSON(json: any): FormField;
export declare function FormFieldToJSONTyped(value?: FormField | null, ignoreDiscriminator?: boolean): any;
