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
 * @interface Flow
 */
export interface Flow {
    /**
     *
     * @type {string}
     * @memberof Flow
     */
    condition?: string;
    /**
     *
     * @type {boolean}
     * @memberof Flow
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof Flow
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof Flow
     */
    name: string;
    /**
     *
     * @type {Array<Step>}
     * @memberof Flow
     */
    post?: Array<Step>;
    /**
     *
     * @type {Array<Step>}
     * @memberof Flow
     */
    pre?: Array<Step>;
    /**
     *
     * @type {string}
     * @memberof Flow
     */
    type: FlowTypeEnum;
}
/**
 * @export
 */
export declare const FlowTypeEnum: {
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
export type FlowTypeEnum = typeof FlowTypeEnum[keyof typeof FlowTypeEnum];
/**
 * Check if a given object implements the Flow interface.
 */
export declare function instanceOfFlow(value: object): value is Flow;
export declare function FlowFromJSON(json: any): Flow;
export declare function FlowFromJSONTyped(json: any, ignoreDiscriminator: boolean): Flow;
export declare function FlowToJSON(json: any): Flow;
export declare function FlowToJSONTyped(value?: Flow | null, ignoreDiscriminator?: boolean): any;
