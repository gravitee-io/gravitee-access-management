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
import type { Step } from './Step';
/**
 *
 * @export
 * @interface FlowEntity
 */
export interface FlowEntity {
    /**
     *
     * @type {string}
     * @memberof FlowEntity
     */
    condition?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof FlowEntity
     */
    createdAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof FlowEntity
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof FlowEntity
     */
    icon?: string;
    /**
     *
     * @type {string}
     * @memberof FlowEntity
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof FlowEntity
     */
    name?: string;
    /**
     *
     * @type {Array<Step>}
     * @memberof FlowEntity
     */
    post?: Array<Step>;
    /**
     *
     * @type {Array<Step>}
     * @memberof FlowEntity
     */
    pre?: Array<Step>;
    /**
     *
     * @type {string}
     * @memberof FlowEntity
     */
    type?: FlowEntityTypeEnum;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof FlowEntity
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const FlowEntityTypeEnum: {
    readonly Root: "ROOT";
    readonly LoginIdentifier: "LOGIN_IDENTIFIER";
    readonly Login: "LOGIN";
    readonly Connect: "CONNECT";
    readonly Consent: "CONSENT";
    readonly Register: "REGISTER";
    readonly ResetPassword: "RESET_PASSWORD";
    readonly RegistrationConfirmation: "REGISTRATION_CONFIRMATION";
    readonly Token: "TOKEN";
    readonly WebauthnRegister: "WEBAUTHN_REGISTER";
    readonly MfaChallenge: "MFA_CHALLENGE";
    readonly MfaEnrollment: "MFA_ENROLLMENT";
};
export type FlowEntityTypeEnum = typeof FlowEntityTypeEnum[keyof typeof FlowEntityTypeEnum];
/**
 * Check if a given object implements the FlowEntity interface.
 */
export declare function instanceOfFlowEntity(value: object): value is FlowEntity;
export declare function FlowEntityFromJSON(json: any): FlowEntity;
export declare function FlowEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): FlowEntity;
export declare function FlowEntityToJSON(json: any): FlowEntity;
export declare function FlowEntityToJSONTyped(value?: FlowEntity | null, ignoreDiscriminator?: boolean): any;
