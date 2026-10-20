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
import type { UpdateProtectedResourceFeature } from './UpdateProtectedResourceFeature';
/**
 *
 * @export
 * @interface UpdateMcpTool
 */
export interface UpdateMcpTool extends UpdateProtectedResourceFeature {
    /**
     *
     * @type {string}
     * @memberof UpdateMcpTool
     */
    type?: UpdateMcpToolTypeEnum;
    /**
     *
     * @type {Array<string>}
     * @memberof UpdateMcpTool
     */
    scopes?: Array<string>;
}
/**
 * @export
 */
export declare const UpdateMcpToolTypeEnum: {
    readonly McpTool: "MCP_TOOL";
};
export type UpdateMcpToolTypeEnum = typeof UpdateMcpToolTypeEnum[keyof typeof UpdateMcpToolTypeEnum];
/**
 * Check if a given object implements the UpdateMcpTool interface.
 */
export declare function instanceOfUpdateMcpTool(value: object): value is UpdateMcpTool;
export declare function UpdateMcpToolFromJSON(json: any): UpdateMcpTool;
export declare function UpdateMcpToolFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateMcpTool;
export declare function UpdateMcpToolToJSON(json: any): UpdateMcpTool;
export declare function UpdateMcpToolToJSONTyped(value?: UpdateMcpTool | null, ignoreDiscriminator?: boolean): any;
