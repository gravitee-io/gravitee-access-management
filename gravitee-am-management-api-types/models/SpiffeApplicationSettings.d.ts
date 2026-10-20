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
 * @interface SpiffeApplicationSettings
 */
export interface SpiffeApplicationSettings {
    /**
     *
     * @type {string}
     * @memberof SpiffeApplicationSettings
     */
    subject?: string;
    /**
     * How the configured `subject` is matched against the SVID `sub` claim.
     * `EXACT` (default) requires equality.
     * `PREFIX` is only allowed for HOSTED_DELEGATED or AUTONOMOUS agent applications;
     * the configured `subject` must end with `/` and the SVID `sub` is accepted when
     * it starts with that subject. The full SVID SPIFFE ID then becomes the
     * per-instance `act.sub` in minted tokens.
     * @type {string}
     * @memberof SpiffeApplicationSettings
     */
    subjectMatchMode?: SpiffeApplicationSettingsSubjectMatchModeEnum;
    /**
     *
     * @type {string}
     * @memberof SpiffeApplicationSettings
     */
    trustDomain?: string;
}
/**
 * @export
 */
export declare const SpiffeApplicationSettingsSubjectMatchModeEnum: {
    readonly Exact: "EXACT";
    readonly Prefix: "PREFIX";
};
export type SpiffeApplicationSettingsSubjectMatchModeEnum = typeof SpiffeApplicationSettingsSubjectMatchModeEnum[keyof typeof SpiffeApplicationSettingsSubjectMatchModeEnum];
/**
 * Check if a given object implements the SpiffeApplicationSettings interface.
 */
export declare function instanceOfSpiffeApplicationSettings(value: object): value is SpiffeApplicationSettings;
export declare function SpiffeApplicationSettingsFromJSON(json: any): SpiffeApplicationSettings;
export declare function SpiffeApplicationSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): SpiffeApplicationSettings;
export declare function SpiffeApplicationSettingsToJSON(json: any): SpiffeApplicationSettings;
export declare function SpiffeApplicationSettingsToJSONTyped(value?: SpiffeApplicationSettings | null, ignoreDiscriminator?: boolean): any;
