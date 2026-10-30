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
    Button,
    Field,
    FieldError,
    FieldLabel,
    Input,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@gravitee/graphene-core';
import { PlusIcon, XIcon } from '@gravitee/graphene-core/icons';
import { useState } from 'react';
import type { CustomField } from './account-settings';

const AVAILABLE_FIELDS: readonly CustomField[] = [
    { key: 'email', label: 'Email', type: 'email' },
    { key: 'username', label: 'Username', type: 'text' },
];

interface CustomFormFieldsProps {
    readonly fields: readonly CustomField[];
    readonly editable: boolean;
    readonly error?: string;
    readonly onChange: (fields: readonly CustomField[]) => void;
}

export function CustomFormFields({ fields, editable, error, onChange }: CustomFormFieldsProps) {
    const [newField, setNewField] = useState<CustomField | undefined>();

    return (
        <div className="flex flex-col gap-4">
            {editable && (
                <div className="flex items-end gap-2">
                    <Field className="w-48">
                        <FieldLabel htmlFor="account-field-key">Field key</FieldLabel>
                        <Select
                            value={newField?.key ?? ''}
                            onValueChange={key => setNewField(AVAILABLE_FIELDS.find(field => field.key === key))}
                        >
                            <SelectTrigger
                                id="account-field-key"
                                className="w-full"
                                aria-describedby={fields.length === 0 && error ? 'account-fields-error' : undefined}
                            >
                                <SelectValue placeholder="Select a field" />
                            </SelectTrigger>
                            <SelectContent>
                                {AVAILABLE_FIELDS.map(field => (
                                    <SelectItem
                                        key={field.key}
                                        value={field.key ?? ''}
                                        disabled={fields.some(selected => selected.key === field.key)}
                                    >
                                        {field.label}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </Field>
                    <Field className="flex-1">
                        <FieldLabel htmlFor="account-field-label">Field label</FieldLabel>
                        <Input
                            id="account-field-label"
                            value={newField?.label ?? ''}
                            disabled={!newField}
                            onChange={event => newField && setNewField({ ...newField, label: event.target.value })}
                        />
                    </Field>
                    <Button
                        type="button"
                        variant="outline"
                        disabled={!newField?.key || !newField.label}
                        onClick={() => {
                            if (newField) {
                                onChange([...fields, newField]);
                                setNewField(undefined);
                            }
                        }}
                    >
                        <PlusIcon aria-hidden />
                        Add
                    </Button>
                </div>
            )}
            {fields.length === 0 ? (
                error && <FieldError id="account-fields-error">{error}</FieldError>
            ) : (
                <Table aria-label="Custom form fields">
                    <TableHeader>
                        <TableRow>
                            <TableHead>Key</TableHead>
                            <TableHead>Label</TableHead>
                            <TableHead>
                                <span className="sr-only">Actions</span>
                            </TableHead>
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        {fields.map(field => (
                            <TableRow key={field.key}>
                                <TableCell className="font-mono text-sm">{field.key}</TableCell>
                                <TableCell>{field.label}</TableCell>
                                <TableCell className="text-right">
                                    {editable && (
                                        <Button
                                            type="button"
                                            variant="ghost"
                                            size="icon-xs"
                                            aria-label={`Remove field ${field.key}`}
                                            onClick={() => onChange(fields.filter(selected => selected.key !== field.key))}
                                        >
                                            <XIcon aria-hidden />
                                        </Button>
                                    )}
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            )}
        </div>
    );
}
