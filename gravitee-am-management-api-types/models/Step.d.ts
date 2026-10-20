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
 * @interface Step
 */
export interface Step {
    /**
     *
     * @type {string}
     * @memberof Step
     */
    condition?: string;
    /**
     *
     * @type {string}
     * @memberof Step
     */
    configuration?: string;
    /**
     *
     * @type {string}
     * @memberof Step
     */
    description?: string;
    /**
     *
     * @type {boolean}
     * @memberof Step
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof Step
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof Step
     */
    policy?: string;
}
/**
 * Check if a given object implements the Step interface.
 */
export declare function instanceOfStep(value: object): value is Step;
export declare function StepFromJSON(json: any): Step;
export declare function StepFromJSONTyped(json: any, ignoreDiscriminator: boolean): Step;
export declare function StepToJSON(json: any): Step;
export declare function StepToJSONTyped(value?: Step | null, ignoreDiscriminator?: boolean): any;
