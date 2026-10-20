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
import type { ApplicationSettings } from './ApplicationSettings';
import type { ClientSecret } from './ClientSecret';
import type { ApplicationIdentityProvider } from './ApplicationIdentityProvider';
import type { ApplicationSecretSettings } from './ApplicationSecretSettings';
/**
 *
 * @export
 * @interface Application
 */
export interface Application {
    /**
     *
     * @type {boolean}
     * @memberof Application
     */
    agentApplication?: boolean;
    /**
     *
     * @type {string}
     * @memberof Application
     */
    certificate?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Application
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof Application
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof Application
     */
    domain?: string;
    /**
     *
     * @type {boolean}
     * @memberof Application
     */
    enabled?: boolean;
    /**
     *
     * @type {Array<string>}
     * @memberof Application
     */
    factors?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof Application
     */
    id?: string;
    /**
     *
     * @type {Array<ApplicationIdentityProvider>}
     * @memberof Application
     */
    identityProviders?: Array<ApplicationIdentityProvider>;
    /**
     *
     * @type {string}
     * @memberof Application
     */
    kind?: ApplicationKindEnum;
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof Application
     */
    metadata?: {
        [key: string]: any;
    };
    /**
     *
     * @type {string}
     * @memberof Application
     */
    name?: string;
    /**
     *
     * @type {Array<ApplicationSecretSettings>}
     * @memberof Application
     */
    secretSettings?: Array<ApplicationSecretSettings>;
    /**
     *
     * @type {Array<ClientSecret>}
     * @memberof Application
     */
    secrets?: Array<ClientSecret>;
    /**
     *
     * @type {ApplicationSettings}
     * @memberof Application
     */
    settings?: ApplicationSettings;
    /**
     *
     * @type {boolean}
     * @memberof Application
     */
    template?: boolean;
    /**
     *
     * @type {string}
     * @memberof Application
     */
    type?: ApplicationTypeEnum;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Application
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const ApplicationKindEnum: {
    readonly UserEmbedded: "USER_EMBEDDED";
    readonly HostedDelegated: "HOSTED_DELEGATED";
    readonly Autonomous: "AUTONOMOUS";
};
export type ApplicationKindEnum = typeof ApplicationKindEnum[keyof typeof ApplicationKindEnum];
/**
 * @export
 */
export declare const ApplicationTypeEnum: {
    readonly Web: "WEB";
    readonly Native: "NATIVE";
    readonly Browser: "BROWSER";
    readonly Service: "SERVICE";
    readonly ResourceServer: "RESOURCE_SERVER";
    readonly Agent: "AGENT";
};
export type ApplicationTypeEnum = typeof ApplicationTypeEnum[keyof typeof ApplicationTypeEnum];
/**
 * Check if a given object implements the Application interface.
 */
export declare function instanceOfApplication(value: object): value is Application;
export declare function ApplicationFromJSON(json: any): Application;
export declare function ApplicationFromJSONTyped(json: any, ignoreDiscriminator: boolean): Application;
export declare function ApplicationToJSON(json: any): Application;
export declare function ApplicationToJSONTyped(value?: Application | null, ignoreDiscriminator?: boolean): any;
