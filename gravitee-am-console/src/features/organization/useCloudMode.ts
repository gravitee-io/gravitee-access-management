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
import { useQuery } from '@tanstack/react-query';
import { getInstallationConfiguration } from '../../lib/api/management-api';

export function useInstallationConfiguration() {
    // A failed lookup is not retried on each mount: it would flip the pending state back and loop through the layout spinner.
    return useQuery({ queryKey: ['installation'], queryFn: getInstallationConfiguration, staleTime: Infinity, retryOnMount: false });
}

interface InstallationQuery {
    readonly isError: boolean;
    readonly data?: { readonly type?: string };
}

/** True when the installation is managed. A failed request counts as managed, so the self-managed actions stay hidden. */
export function isManaged(installation: InstallationQuery): boolean {
    return installation.isError || installation.data?.type === 'managed';
}

/** True when the installation is managed. Read as managed until the installation type loads. */
export function useCloudMode(): boolean {
    const installation = useInstallationConfiguration();
    return installation.isPending || isManaged(installation);
}
