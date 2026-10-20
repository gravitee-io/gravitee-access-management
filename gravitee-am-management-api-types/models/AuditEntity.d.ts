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
 * @interface AuditEntity
 */
export interface AuditEntity {
    /**
     *
     * @type {string}
     * @memberof AuditEntity
     */
    alternativeId?: string;
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof AuditEntity
     */
    attributes?: {
        [key: string]: any;
    };
    /**
     *
     * @type {string}
     * @memberof AuditEntity
     */
    displayName?: string;
    /**
     *
     * @type {string}
     * @memberof AuditEntity
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof AuditEntity
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof AuditEntity
     */
    referenceType?: AuditEntityReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof AuditEntity
     */
    type?: string;
}
/**
 * @export
 */
export declare const AuditEntityReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type AuditEntityReferenceTypeEnum = typeof AuditEntityReferenceTypeEnum[keyof typeof AuditEntityReferenceTypeEnum];
/**
 * Check if a given object implements the AuditEntity interface.
 */
export declare function instanceOfAuditEntity(value: object): value is AuditEntity;
export declare function AuditEntityFromJSON(json: any): AuditEntity;
export declare function AuditEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): AuditEntity;
export declare function AuditEntityToJSON(json: any): AuditEntity;
export declare function AuditEntityToJSONTyped(value?: AuditEntity | null, ignoreDiscriminator?: boolean): any;
