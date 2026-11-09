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
import {
    Combobox,
    ComboboxContent,
    ComboboxEmpty,
    ComboboxInput,
    ComboboxItem,
    ComboboxList,
    useDebouncedValue,
} from '@gravitee/graphene-core';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { searchApplications } from '../../lib/api/applications';
import { useCurrentDomain } from '../../lib/session/domain';

export type PickedApplication = { readonly id: string; readonly name: string };

interface ApplicationPickerProps {
    readonly id: string;
    readonly value: PickedApplication | null;
    readonly onChange: (application: PickedApplication | null) => void;
    readonly describedBy?: string;
}

/** The Angular `app-select-applications`: searches the domain applications by name or client id. */
export function ApplicationPicker({ id, value, onChange, describedBy }: ApplicationPickerProps) {
    const { ref } = useCurrentDomain();
    const [open, setOpen] = useState(false);
    const [text, setText] = useState('');
    const query = useDebouncedValue(text);
    const applications = useQuery({
        queryKey: ['applications', ref, 'search', query],
        queryFn: () => searchApplications(ref, query),
        enabled: open,
        placeholderData: keepPreviousData,
    });
    const items: PickedApplication[] = (applications.data?.data ?? []).map(({ id: appId, name }) => ({ id: appId, name }));

    return (
        <Combobox
            items={items}
            filter={null}
            value={value}
            onValueChange={application => onChange(application)}
            itemToStringLabel={(application: PickedApplication) => application.name}
            isItemEqualToValue={(item: PickedApplication, selected: PickedApplication) => item.id === selected.id}
            onInputValueChange={(input, details) => details.reason === 'input-change' && setText(input)}
            onOpenChange={setOpen}
        >
            <ComboboxInput id={id} placeholder="Search for an application..." aria-describedby={describedBy} showClear />
            <ComboboxContent>
                <ComboboxEmpty>{applications.isFetching ? 'Searching…' : 'No application found.'}</ComboboxEmpty>
                <ComboboxList>
                    {(application: PickedApplication) => (
                        <ComboboxItem key={application.id} value={application}>
                            {application.name}
                        </ComboboxItem>
                    )}
                </ComboboxList>
            </ComboboxContent>
        </Combobox>
    );
}
