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
 * @interface AlertServiceStatusEntity
 */
export interface AlertServiceStatusEntity {
    /**
     *
     * @type {boolean}
     * @memberof AlertServiceStatusEntity
     */
    available?: boolean;
}
/**
 * Check if a given object implements the AlertServiceStatusEntity interface.
 */
export declare function instanceOfAlertServiceStatusEntity(value: object): value is AlertServiceStatusEntity;
export declare function AlertServiceStatusEntityFromJSON(json: any): AlertServiceStatusEntity;
export declare function AlertServiceStatusEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): AlertServiceStatusEntity;
export declare function AlertServiceStatusEntityToJSON(json: any): AlertServiceStatusEntity;
export declare function AlertServiceStatusEntityToJSONTyped(value?: AlertServiceStatusEntity | null, ignoreDiscriminator?: boolean): any;
