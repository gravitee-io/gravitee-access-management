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
 * @interface UpdateAuthorizationEngine
 */
export interface UpdateAuthorizationEngine {
    /**
     *
     * @type {string}
     * @memberof UpdateAuthorizationEngine
     */
    configuration: string;
    /**
     *
     * @type {string}
     * @memberof UpdateAuthorizationEngine
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof UpdateAuthorizationEngine
     */
    type?: string;
}
/**
 * Check if a given object implements the UpdateAuthorizationEngine interface.
 */
export declare function instanceOfUpdateAuthorizationEngine(value: object): value is UpdateAuthorizationEngine;
export declare function UpdateAuthorizationEngineFromJSON(json: any): UpdateAuthorizationEngine;
export declare function UpdateAuthorizationEngineFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateAuthorizationEngine;
export declare function UpdateAuthorizationEngineToJSON(json: any): UpdateAuthorizationEngine;
export declare function UpdateAuthorizationEngineToJSONTyped(value?: UpdateAuthorizationEngine | null, ignoreDiscriminator?: boolean): any;
