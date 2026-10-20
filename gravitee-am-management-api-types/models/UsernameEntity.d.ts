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
 * @interface UsernameEntity
 */
export interface UsernameEntity {
    /**
     *
     * @type {string}
     * @memberof UsernameEntity
     */
    username: string;
}
/**
 * Check if a given object implements the UsernameEntity interface.
 */
export declare function instanceOfUsernameEntity(value: object): value is UsernameEntity;
export declare function UsernameEntityFromJSON(json: any): UsernameEntity;
export declare function UsernameEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): UsernameEntity;
export declare function UsernameEntityToJSON(json: any): UsernameEntity;
export declare function UsernameEntityToJSONTyped(value?: UsernameEntity | null, ignoreDiscriminator?: boolean): any;
