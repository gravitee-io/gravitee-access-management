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
 * @interface DataPlane
 */
export interface DataPlane {
    /**
     *
     * @type {any}
     * @memberof DataPlane
     */
    delegate?: any;
}
/**
 * Check if a given object implements the DataPlane interface.
 */
export declare function instanceOfDataPlane(value: object): value is DataPlane;
export declare function DataPlaneFromJSON(json: any): DataPlane;
export declare function DataPlaneFromJSONTyped(json: any, ignoreDiscriminator: boolean): DataPlane;
export declare function DataPlaneToJSON(json: any): DataPlane;
export declare function DataPlaneToJSONTyped(value?: DataPlane | null, ignoreDiscriminator?: boolean): any;
