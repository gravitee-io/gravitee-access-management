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
import { toast } from '@gravitee/graphene-core';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { Domain } from '../../lib/api/domains';
import { type UserPreferences, updateUserPreferences } from '../../lib/api/management-api';
import { useUserPreferences } from '../../lib/session/preferences';

const NO_PINS: readonly string[] = [];

/** The pinned and default domains of the user, and the actions that change them. */
export function useDomainPreferences(environmentId: string) {
    const queryClient = useQueryClient();
    const preferences = useUserPreferences();
    const pinnedIds = preferences.data?.pinnedDomainIds ?? NO_PINS;
    const defaultDomainId = preferences.data?.defaultDomainId;

    const save = useMutation({
        mutationFn: ({ next }: { next: UserPreferences; message: string }) => updateUserPreferences(next),
        onSuccess: (updated, { message }) => {
            queryClient.setQueryData(['user-preferences'], updated);
            toast.success(message);
        },
        onError: failure => toast.error(`Could not update your preferences. ${failure.message}`),
    });

    const togglePin = (domain: Domain) => {
        const isPinned = pinnedIds.includes(domain.id);
        save.mutate({
            next: {
                ...preferences.data,
                pinnedDomainIds: isPinned ? pinnedIds.filter(id => id !== domain.id) : [...pinnedIds, domain.id],
            },
            message: isPinned ? `${domain.name} unpinned` : `${domain.name} pinned`,
        });
    };

    const toggleDefault = (domain: Domain) => {
        const unset = defaultDomainId === domain.id;
        save.mutate({
            next: {
                ...preferences.data,
                defaultDomainId: unset ? null : domain.id,
                defaultEnvironmentId: unset ? null : environmentId,
            },
            message: unset ? 'Default domain unset' : `${domain.name} is now your default domain`,
        });
    };

    return {
        pinnedIds,
        defaultDomainId,
        error: preferences.error,
        // The PUT replaces every preference, so an update must start from the loaded ones.
        canSave: preferences.isSuccess && !save.isPending,
        togglePin,
        toggleDefault,
    };
}
