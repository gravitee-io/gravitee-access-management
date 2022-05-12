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
export interface NewApplication {
    name: string;
    type: ApplicationType;
    description?: string;
    clientId?: string;
    clientSecret?: string;
    redirectUris?: string[];
    metadata?: any;
}

export enum ApplicationType {
    WEB = 'WEB',
    NATIVE = 'NATIVE',
    BROWSER = 'BROWSER',
    SERVICE = 'SERVICE',
    RESOURCE_SERVER = 'RESOURCE_SERVER',
}

export interface Application {
    id: string;
    name: string;
    type: ApplicationType;
    description: string;
    domain: string;
    enabled: boolean;
    template: boolean;
    identities: string[];
    factors: string[];
    certificate: string;
    metadata: any;
    settings: any;
    createdAt: string;
    updatedAt: string;
    passwordSettings: any;
}
