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
 * @interface Reference
 */
export interface Reference {
    /**
     *
     * @type {string}
     * @memberof Reference
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof Reference
     */
    type?: ReferenceTypeEnum;
}
/**
 * @export
 */
export declare const ReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type ReferenceTypeEnum = typeof ReferenceTypeEnum[keyof typeof ReferenceTypeEnum];
/**
 * Check if a given object implements the Reference interface.
 */
export declare function instanceOfReference(value: object): value is Reference;
export declare function ReferenceFromJSON(json: any): Reference;
export declare function ReferenceFromJSONTyped(json: any, ignoreDiscriminator: boolean): Reference;
export declare function ReferenceToJSON(json: any): Reference;
export declare function ReferenceToJSONTyped(value?: Reference | null, ignoreDiscriminator?: boolean): any;
