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
 */
export declare const ManagedBy: {
    readonly None: "NONE";
    readonly AutomationApi: "AUTOMATION_API";
};
export type ManagedBy = typeof ManagedBy[keyof typeof ManagedBy];
export declare function instanceOfManagedBy(value: any): boolean;
export declare function ManagedByFromJSON(json: any): ManagedBy;
export declare function ManagedByFromJSONTyped(json: any, ignoreDiscriminator: boolean): ManagedBy;
export declare function ManagedByToJSON(value?: ManagedBy | null): any;
export declare function ManagedByToJSONTyped(value: any, ignoreDiscriminator: boolean): ManagedBy;
