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
 * @interface AnalyticsTypeParam
 */
export interface AnalyticsTypeParam {
    /**
     *
     * @type {string}
     * @memberof AnalyticsTypeParam
     */
    originalParam?: string;
    /**
     *
     * @type {string}
     * @memberof AnalyticsTypeParam
     */
    value?: AnalyticsTypeParamValueEnum;
}
/**
 * @export
 */
export declare const AnalyticsTypeParamValueEnum: {
    readonly DateHisto: "DATE_HISTO";
    readonly Count: "COUNT";
    readonly GroupBy: "GROUP_BY";
};
export type AnalyticsTypeParamValueEnum = typeof AnalyticsTypeParamValueEnum[keyof typeof AnalyticsTypeParamValueEnum];
/**
 * Check if a given object implements the AnalyticsTypeParam interface.
 */
export declare function instanceOfAnalyticsTypeParam(value: object): value is AnalyticsTypeParam;
export declare function AnalyticsTypeParamFromJSON(json: any): AnalyticsTypeParam;
export declare function AnalyticsTypeParamFromJSONTyped(json: any, ignoreDiscriminator: boolean): AnalyticsTypeParam;
export declare function AnalyticsTypeParamToJSON(json: any): AnalyticsTypeParam;
export declare function AnalyticsTypeParamToJSONTyped(value?: AnalyticsTypeParam | null, ignoreDiscriminator?: boolean): any;
