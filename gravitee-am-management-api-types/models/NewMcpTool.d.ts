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
import type { NewProtectedResourceFeature } from './NewProtectedResourceFeature';
/**
 *
 * @export
 * @interface NewMcpTool
 */
export interface NewMcpTool extends NewProtectedResourceFeature {
    /**
     *
     * @type {string}
     * @memberof NewMcpTool
     */
    type?: NewMcpToolTypeEnum;
    /**
     *
     * @type {Array<string>}
     * @memberof NewMcpTool
     */
    scopes?: Array<string>;
}
/**
 * @export
 */
export declare const NewMcpToolTypeEnum: {
    readonly McpTool: "MCP_TOOL";
};
export type NewMcpToolTypeEnum = typeof NewMcpToolTypeEnum[keyof typeof NewMcpToolTypeEnum];
/**
 * Check if a given object implements the NewMcpTool interface.
 */
export declare function instanceOfNewMcpTool(value: object): value is NewMcpTool;
export declare function NewMcpToolFromJSON(json: any): NewMcpTool;
export declare function NewMcpToolFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewMcpTool;
export declare function NewMcpToolToJSON(json: any): NewMcpTool;
export declare function NewMcpToolToJSONTyped(value?: NewMcpTool | null, ignoreDiscriminator?: boolean): any;
