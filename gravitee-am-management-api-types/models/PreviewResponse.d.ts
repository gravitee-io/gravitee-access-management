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
 * @interface PreviewResponse
 */
export interface PreviewResponse {
    /**
     *
     * @type {string}
     * @memberof PreviewResponse
     */
    content?: string;
    /**
     *
     * @type {string}
     * @memberof PreviewResponse
     */
    template?: string;
    /**
     *
     * @type {string}
     * @memberof PreviewResponse
     */
    type?: PreviewResponseTypeEnum;
}
/**
 * @export
 */
export declare const PreviewResponseTypeEnum: {
    readonly Email: "EMAIL";
    readonly Form: "FORM";
};
export type PreviewResponseTypeEnum = typeof PreviewResponseTypeEnum[keyof typeof PreviewResponseTypeEnum];
/**
 * Check if a given object implements the PreviewResponse interface.
 */
export declare function instanceOfPreviewResponse(value: object): value is PreviewResponse;
export declare function PreviewResponseFromJSON(json: any): PreviewResponse;
export declare function PreviewResponseFromJSONTyped(json: any, ignoreDiscriminator: boolean): PreviewResponse;
export declare function PreviewResponseToJSON(json: any): PreviewResponse;
export declare function PreviewResponseToJSONTyped(value?: PreviewResponse | null, ignoreDiscriminator?: boolean): any;
