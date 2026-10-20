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
 * @interface ApplicationEntity
 */
export interface ApplicationEntity {
    /**
     *
     * @type {string}
     * @memberof ApplicationEntity
     */
    clientId?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationEntity
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationEntity
     */
    name?: string;
}
/**
 * Check if a given object implements the ApplicationEntity interface.
 */
export declare function instanceOfApplicationEntity(value: object): value is ApplicationEntity;
export declare function ApplicationEntityFromJSON(json: any): ApplicationEntity;
export declare function ApplicationEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): ApplicationEntity;
export declare function ApplicationEntityToJSON(json: any): ApplicationEntity;
export declare function ApplicationEntityToJSONTyped(value?: ApplicationEntity | null, ignoreDiscriminator?: boolean): any;
