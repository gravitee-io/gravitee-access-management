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
 * @interface EnrolledFactorChannel
 */
export interface EnrolledFactorChannel {
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof EnrolledFactorChannel
     */
    additionalData?: {
        [key: string]: any;
    };
    /**
     *
     * @type {string}
     * @memberof EnrolledFactorChannel
     */
    target?: string;
    /**
     *
     * @type {string}
     * @memberof EnrolledFactorChannel
     */
    type?: EnrolledFactorChannelTypeEnum;
}
/**
 * @export
 */
export declare const EnrolledFactorChannelTypeEnum: {
    readonly Sms: "SMS";
    readonly Email: "EMAIL";
    readonly Http: "HTTP";
    readonly Call: "CALL";
};
export type EnrolledFactorChannelTypeEnum = typeof EnrolledFactorChannelTypeEnum[keyof typeof EnrolledFactorChannelTypeEnum];
/**
 * Check if a given object implements the EnrolledFactorChannel interface.
 */
export declare function instanceOfEnrolledFactorChannel(value: object): value is EnrolledFactorChannel;
export declare function EnrolledFactorChannelFromJSON(json: any): EnrolledFactorChannel;
export declare function EnrolledFactorChannelFromJSONTyped(json: any, ignoreDiscriminator: boolean): EnrolledFactorChannel;
export declare function EnrolledFactorChannelToJSON(json: any): EnrolledFactorChannel;
export declare function EnrolledFactorChannelToJSONTyped(value?: EnrolledFactorChannel | null, ignoreDiscriminator?: boolean): any;
