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
 * @interface NewCimdApplication
 */
export interface NewCimdApplication {
    /**
     *
     * @type {string}
     * @memberof NewCimdApplication
     */
    cimdUrl: string;
    /**
     *
     * @type {string}
     * @memberof NewCimdApplication
     */
    clientName?: string;
    /**
     *
     * @type {string}
     * @memberof NewCimdApplication
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof NewCimdApplication
     */
    kind?: NewCimdApplicationKindEnum;
    /**
     *
     * @type {string}
     * @memberof NewCimdApplication
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof NewCimdApplication
     */
    type: NewCimdApplicationTypeEnum;
}
/**
 * @export
 */
export declare const NewCimdApplicationKindEnum: {
    readonly UserEmbedded: "USER_EMBEDDED";
    readonly HostedDelegated: "HOSTED_DELEGATED";
    readonly Autonomous: "AUTONOMOUS";
};
export type NewCimdApplicationKindEnum = typeof NewCimdApplicationKindEnum[keyof typeof NewCimdApplicationKindEnum];
/**
 * @export
 */
export declare const NewCimdApplicationTypeEnum: {
    readonly Web: "WEB";
    readonly Native: "NATIVE";
    readonly Browser: "BROWSER";
    readonly Service: "SERVICE";
    readonly ResourceServer: "RESOURCE_SERVER";
    readonly Agent: "AGENT";
};
export type NewCimdApplicationTypeEnum = typeof NewCimdApplicationTypeEnum[keyof typeof NewCimdApplicationTypeEnum];
/**
 * Check if a given object implements the NewCimdApplication interface.
 */
export declare function instanceOfNewCimdApplication(value: object): value is NewCimdApplication;
export declare function NewCimdApplicationFromJSON(json: any): NewCimdApplication;
export declare function NewCimdApplicationFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewCimdApplication;
export declare function NewCimdApplicationToJSON(json: any): NewCimdApplication;
export declare function NewCimdApplicationToJSONTyped(value?: NewCimdApplication | null, ignoreDiscriminator?: boolean): any;
