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
    ComboboxChip,
    ComboboxChips,
    ComboboxChipsInput,
    ComboboxContent,
    ComboboxEmpty,
    ComboboxItem,
    ComboboxList,
    Field,
    FieldLabel,
} from '@gravitee/graphene-core';
import type { OrganizationIdentityProvider } from '../../lib/api/organization';

interface ProviderSelectProps {
    readonly id: string;
    readonly label: string;
    readonly placeholder: string;
    readonly options: readonly OrganizationIdentityProvider[];
    readonly value: readonly string[];
    readonly disabled: boolean;
    readonly onChange: (ids: string[]) => void;
}

export function ProviderSelect({ id, label, placeholder, options, value, disabled, onChange }: ProviderSelectProps) {
    const items = options.map(option => ({ value: option.id, label: option.name }));
    const selected = items.filter(item => value.includes(item.value));
    return (
        <Field>
            <FieldLabel htmlFor={id} className="sr-only">
                {label}
            </FieldLabel>
            <Combobox
                multiple
                items={items}
                value={selected}
                onValueChange={next => onChange(next.map(item => item.value))}
                isItemEqualToValue={(item, current) => item.value === current.value}
                disabled={disabled}
            >
                <ComboboxChips>
                    {selected.map(item => (
                        <ComboboxChip key={item.value} removeAriaLabel={`Remove ${item.label}`}>
                            {item.label}
                        </ComboboxChip>
                    ))}
                    <ComboboxChipsInput id={id} placeholder={selected.length === 0 ? placeholder : undefined} />
                </ComboboxChips>
                <ComboboxContent>
                    <ComboboxEmpty>No provider found.</ComboboxEmpty>
                    <ComboboxList>
                        {item => (
                            <ComboboxItem key={item.value} value={item}>
                                {item.label}
                            </ComboboxItem>
                        )}
                    </ComboboxList>
                </ComboboxContent>
            </Combobox>
        </Field>
    );
}
