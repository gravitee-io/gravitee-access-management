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
import type { Theme } from './Theme';
/**
 *
 * @export
 * @interface PreviewRequest
 */
export interface PreviewRequest {
    /**
     *
     * @type {string}
     * @memberof PreviewRequest
     */
    content?: string;
    /**
     *
     * @type {string}
     * @memberof PreviewRequest
     */
    template: string;
    /**
     *
     * @type {Theme}
     * @memberof PreviewRequest
     */
    theme?: Theme;
    /**
     *
     * @type {string}
     * @memberof PreviewRequest
     */
    type: PreviewRequestTypeEnum;
}
/**
 * @export
 */
export declare const PreviewRequestTypeEnum: {
    readonly Email: "EMAIL";
    readonly Form: "FORM";
};
export type PreviewRequestTypeEnum = typeof PreviewRequestTypeEnum[keyof typeof PreviewRequestTypeEnum];
/**
 * Check if a given object implements the PreviewRequest interface.
 */
export declare function instanceOfPreviewRequest(value: object): value is PreviewRequest;
export declare function PreviewRequestFromJSON(json: any): PreviewRequest;
export declare function PreviewRequestFromJSONTyped(json: any, ignoreDiscriminator: boolean): PreviewRequest;
export declare function PreviewRequestToJSON(json: any): PreviewRequest;
export declare function PreviewRequestToJSONTyped(value?: PreviewRequest | null, ignoreDiscriminator?: boolean): any;
