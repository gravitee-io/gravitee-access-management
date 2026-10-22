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
import { Input, Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@gravitee/graphene-core';
import { DURATION_UNITS, type DurationDraft, type DurationUnit } from '../../lib/forms/duration';

interface DurationInputProps {
    /** The id of the amount input, for its `FieldLabel`. */
    readonly id: string;
    /** The field label. The unit select is named "<label> unit". */
    readonly label: string;
    readonly value: DurationDraft;
    readonly disabled?: boolean;
    readonly invalid?: boolean;
    readonly onChange: (value: DurationDraft) => void;
}

/** An amount and a unit, as the Angular duration fields with their `units` select. */
export function DurationInput({ id, label, value, disabled, invalid, onChange }: DurationInputProps) {
    return (
        <div className="flex gap-2">
            <Input
                id={id}
                type="number"
                min={1}
                className="flex-1"
                value={value.time}
                disabled={disabled}
                aria-invalid={invalid}
                onChange={event => onChange({ ...value, time: event.target.value })}
            />
            <Select
                value={value.unit}
                disabled={disabled}
                onValueChange={unit => unit && onChange({ ...value, unit: unit as DurationUnit })}
            >
                <SelectTrigger className="w-48" aria-label={`${label} unit`}>
                    <SelectValue placeholder="Unit" />
                </SelectTrigger>
                <SelectContent>
                    {DURATION_UNITS.map(unit => (
                        <SelectItem key={unit.value} value={unit.value}>
                            {unit.label}
                        </SelectItem>
                    ))}
                </SelectContent>
            </Select>
        </div>
    );
}
