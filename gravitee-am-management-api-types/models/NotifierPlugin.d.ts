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
 * @interface NotifierPlugin
 */
export interface NotifierPlugin {
    /**
     *
     * @type {boolean}
     * @memberof NotifierPlugin
     */
    deployed?: boolean;
    /**
     *
     * @type {string}
     * @memberof NotifierPlugin
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof NotifierPlugin
     */
    displayName?: string;
    /**
     *
     * @type {string}
     * @memberof NotifierPlugin
     */
    feature?: string;
    /**
     *
     * @type {string}
     * @memberof NotifierPlugin
     */
    icon?: string;
    /**
     *
     * @type {string}
     * @memberof NotifierPlugin
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof NotifierPlugin
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof NotifierPlugin
     */
    version?: string;
}
/**
 * Check if a given object implements the NotifierPlugin interface.
 */
export declare function instanceOfNotifierPlugin(value: object): value is NotifierPlugin;
export declare function NotifierPluginFromJSON(json: any): NotifierPlugin;
export declare function NotifierPluginFromJSONTyped(json: any, ignoreDiscriminator: boolean): NotifierPlugin;
export declare function NotifierPluginToJSON(json: any): NotifierPlugin;
export declare function NotifierPluginToJSONTyped(value?: NotifierPlugin | null, ignoreDiscriminator?: boolean): any;
