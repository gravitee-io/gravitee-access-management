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
 * @interface PatchApplicationType
 */
export interface PatchApplicationType {
    /**
     *
     * @type {string}
     * @memberof PatchApplicationType
     */
    type: PatchApplicationTypeTypeEnum;
}
/**
 * @export
 */
export declare const PatchApplicationTypeTypeEnum: {
    readonly Web: "WEB";
    readonly Native: "NATIVE";
    readonly Browser: "BROWSER";
    readonly Service: "SERVICE";
    readonly ResourceServer: "RESOURCE_SERVER";
    readonly Agent: "AGENT";
};
export type PatchApplicationTypeTypeEnum = typeof PatchApplicationTypeTypeEnum[keyof typeof PatchApplicationTypeTypeEnum];
/**
 * Check if a given object implements the PatchApplicationType interface.
 */
export declare function instanceOfPatchApplicationType(value: object): value is PatchApplicationType;
export declare function PatchApplicationTypeFromJSON(json: any): PatchApplicationType;
export declare function PatchApplicationTypeFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchApplicationType;
export declare function PatchApplicationTypeToJSON(json: any): PatchApplicationType;
export declare function PatchApplicationTypeToJSONTyped(value?: PatchApplicationType | null, ignoreDiscriminator?: boolean): any;
