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
 * A host and path the domain is exposed on. The host and path combination must be unique across all domains.
 * @export
 * @interface VirtualHost
 */
export interface VirtualHost {
    /**
     * Hostname the domain is served on.
     * @type {string}
     * @memberof VirtualHost
     */
    host?: string;
    /**
     * Whether this virtual host overrides the organization entry point.
     * @type {boolean}
     * @memberof VirtualHost
     */
    overrideEntrypoint?: boolean;
    /**
     * Context path the domain is served under on this host.
     * @type {string}
     * @memberof VirtualHost
     */
    path?: string;
}
/**
 * Check if a given object implements the VirtualHost interface.
 */
export declare function instanceOfVirtualHost(value: object): value is VirtualHost;
export declare function VirtualHostFromJSON(json: any): VirtualHost;
export declare function VirtualHostFromJSONTyped(json: any, ignoreDiscriminator: boolean): VirtualHost;
export declare function VirtualHostToJSON(json: any): VirtualHost;
export declare function VirtualHostToJSONTyped(value?: VirtualHost | null, ignoreDiscriminator?: boolean): any;
