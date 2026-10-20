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
 * @interface AuditAccessPoint
 */
export interface AuditAccessPoint {
    /**
     *
     * @type {string}
     * @memberof AuditAccessPoint
     */
    alternativeId?: string;
    /**
     *
     * @type {string}
     * @memberof AuditAccessPoint
     */
    displayName?: string;
    /**
     *
     * @type {string}
     * @memberof AuditAccessPoint
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof AuditAccessPoint
     */
    ipAddress?: string;
    /**
     *
     * @type {string}
     * @memberof AuditAccessPoint
     */
    userAgent?: string;
}
/**
 * Check if a given object implements the AuditAccessPoint interface.
 */
export declare function instanceOfAuditAccessPoint(value: object): value is AuditAccessPoint;
export declare function AuditAccessPointFromJSON(json: any): AuditAccessPoint;
export declare function AuditAccessPointFromJSONTyped(json: any, ignoreDiscriminator: boolean): AuditAccessPoint;
export declare function AuditAccessPointToJSON(json: any): AuditAccessPoint;
export declare function AuditAccessPointToJSONTyped(value?: AuditAccessPoint | null, ignoreDiscriminator?: boolean): any;
