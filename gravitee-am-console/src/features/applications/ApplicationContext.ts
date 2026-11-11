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
import { createContext, use } from 'react';
import type { Application } from '../../lib/api/applications';
import type { DomainRef } from '../../lib/api/paths';

export interface ApplicationContextValue {
    readonly application: Application;
    /** The URL of the application, such as `.../applications/:appId`. Section paths join onto it. */
    readonly applicationPath: string;
    readonly listPath: string;
}

export const ApplicationContext = createContext<ApplicationContextValue | null>(null);

export function useApplication(): ApplicationContextValue {
    const value = use(ApplicationContext);
    if (!value) {
        throw new Error('useApplication needs ApplicationLayout above it');
    }
    return value;
}

export function applicationQueryKey(ref: DomainRef, applicationId: string) {
    return ['application', ref, applicationId];
}
