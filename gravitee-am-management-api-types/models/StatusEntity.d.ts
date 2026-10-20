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
 * @interface StatusEntity
 */
export interface StatusEntity {
    /**
     *
     * @type {boolean}
     * @memberof StatusEntity
     */
    enabled?: boolean;
}
/**
 * Check if a given object implements the StatusEntity interface.
 */
export declare function instanceOfStatusEntity(value: object): value is StatusEntity;
export declare function StatusEntityFromJSON(json: any): StatusEntity;
export declare function StatusEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): StatusEntity;
export declare function StatusEntityToJSON(json: any): StatusEntity;
export declare function StatusEntityToJSONTyped(value?: StatusEntity | null, ignoreDiscriminator?: boolean): any;
