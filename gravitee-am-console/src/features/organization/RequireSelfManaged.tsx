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
import { Spinner } from '@gravitee/graphene-core';
import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { isManaged, useInstallationConfiguration } from './useCloudMode';
import { useOrganizationPath } from './useOrganizationPath';

/** Cloud mode hides the page. The route leaves for the first page of the section, as the Angular `CloudModeGuard` cancels the navigation. */
export function RequireSelfManaged({ children }: { readonly children: ReactNode }) {
    const base = useOrganizationPath();
    const installation = useInstallationConfiguration();
    if (installation.isPending) {
        return <Spinner />;
    }
    return isManaged(installation.data) ? <Navigate to={`${base}/general`} replace /> : children;
}
