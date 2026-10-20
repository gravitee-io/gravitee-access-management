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
 * @interface NewApplication
 */
export interface NewApplication {
    /**
     *
     * @type {string}
     * @memberof NewApplication
     */
    clientId?: string;
    /**
     *
     * @type {string}
     * @memberof NewApplication
     */
    clientSecret?: string;
    /**
     *
     * @type {string}
     * @memberof NewApplication
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof NewApplication
     */
    kind?: NewApplicationKindEnum;
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof NewApplication
     */
    metadata?: {
        [key: string]: any;
    };
    /**
     *
     * @type {string}
     * @memberof NewApplication
     */
    name: string;
    /**
     *
     * @type {Array<string>}
     * @memberof NewApplication
     */
    redirectUris?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof NewApplication
     */
    type: NewApplicationTypeEnum;
}
/**
 * @export
 */
export declare const NewApplicationKindEnum: {
    readonly UserEmbedded: "USER_EMBEDDED";
    readonly HostedDelegated: "HOSTED_DELEGATED";
    readonly Autonomous: "AUTONOMOUS";
};
export type NewApplicationKindEnum = typeof NewApplicationKindEnum[keyof typeof NewApplicationKindEnum];
/**
 * @export
 */
export declare const NewApplicationTypeEnum: {
    readonly Web: "WEB";
    readonly Native: "NATIVE";
    readonly Browser: "BROWSER";
    readonly Service: "SERVICE";
    readonly ResourceServer: "RESOURCE_SERVER";
    readonly Agent: "AGENT";
};
export type NewApplicationTypeEnum = typeof NewApplicationTypeEnum[keyof typeof NewApplicationTypeEnum];
/**
 * Check if a given object implements the NewApplication interface.
 */
export declare function instanceOfNewApplication(value: object): value is NewApplication;
export declare function NewApplicationFromJSON(json: any): NewApplication;
export declare function NewApplicationFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewApplication;
export declare function NewApplicationToJSON(json: any): NewApplication;
export declare function NewApplicationToJSONTyped(value?: NewApplication | null, ignoreDiscriminator?: boolean): any;
