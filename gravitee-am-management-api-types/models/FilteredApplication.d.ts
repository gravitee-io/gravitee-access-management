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
 * @interface FilteredApplication
 */
export interface FilteredApplication {
    /**
     *
     * @type {string}
     * @memberof FilteredApplication
     */
    clientId?: string;
    /**
     *
     * @type {string}
     * @memberof FilteredApplication
     */
    description?: string;
    /**
     *
     * @type {boolean}
     * @memberof FilteredApplication
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof FilteredApplication
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof FilteredApplication
     */
    kind?: FilteredApplicationKindEnum;
    /**
     *
     * @type {string}
     * @memberof FilteredApplication
     */
    name?: string;
    /**
     *
     * @type {boolean}
     * @memberof FilteredApplication
     */
    template?: boolean;
    /**
     *
     * @type {string}
     * @memberof FilteredApplication
     */
    type?: FilteredApplicationTypeEnum;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof FilteredApplication
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const FilteredApplicationKindEnum: {
    readonly UserEmbedded: "USER_EMBEDDED";
    readonly HostedDelegated: "HOSTED_DELEGATED";
    readonly Autonomous: "AUTONOMOUS";
};
export type FilteredApplicationKindEnum = typeof FilteredApplicationKindEnum[keyof typeof FilteredApplicationKindEnum];
/**
 * @export
 */
export declare const FilteredApplicationTypeEnum: {
    readonly Web: "WEB";
    readonly Native: "NATIVE";
    readonly Browser: "BROWSER";
    readonly Service: "SERVICE";
    readonly ResourceServer: "RESOURCE_SERVER";
    readonly Agent: "AGENT";
};
export type FilteredApplicationTypeEnum = typeof FilteredApplicationTypeEnum[keyof typeof FilteredApplicationTypeEnum];
/**
 * Check if a given object implements the FilteredApplication interface.
 */
export declare function instanceOfFilteredApplication(value: object): value is FilteredApplication;
export declare function FilteredApplicationFromJSON(json: any): FilteredApplication;
export declare function FilteredApplicationFromJSONTyped(json: any, ignoreDiscriminator: boolean): FilteredApplication;
export declare function FilteredApplicationToJSON(json: any): FilteredApplication;
export declare function FilteredApplicationToJSONTyped(value?: FilteredApplication | null, ignoreDiscriminator?: boolean): any;
