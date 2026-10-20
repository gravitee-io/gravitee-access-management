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
 * @interface EnrolledFactorSecurity
 */
export interface EnrolledFactorSecurity {
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof EnrolledFactorSecurity
     */
    additionalData?: {
        [key: string]: any;
    };
    /**
     *
     * @type {string}
     * @memberof EnrolledFactorSecurity
     */
    type?: string;
    /**
     *
     * @type {string}
     * @memberof EnrolledFactorSecurity
     */
    value?: string;
}
/**
 * Check if a given object implements the EnrolledFactorSecurity interface.
 */
export declare function instanceOfEnrolledFactorSecurity(value: object): value is EnrolledFactorSecurity;
export declare function EnrolledFactorSecurityFromJSON(json: any): EnrolledFactorSecurity;
export declare function EnrolledFactorSecurityFromJSONTyped(json: any, ignoreDiscriminator: boolean): EnrolledFactorSecurity;
export declare function EnrolledFactorSecurityToJSON(json: any): EnrolledFactorSecurity;
export declare function EnrolledFactorSecurityToJSONTyped(value?: EnrolledFactorSecurity | null, ignoreDiscriminator?: boolean): any;
