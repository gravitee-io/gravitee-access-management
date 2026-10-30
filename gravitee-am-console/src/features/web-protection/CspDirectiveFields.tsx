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
    Combobox,
    ComboboxContent,
    ComboboxInput,
    ComboboxItem,
    ComboboxList,
    Field,
    FieldError,
    FieldLabel,
    Input,
    Tooltip,
    TooltipContent,
    TooltipTrigger,
} from '@gravitee/graphene-core';
import { InfoIcon, Trash2Icon, TriangleAlertIcon } from '@gravitee/graphene-core/icons';
import { type CspDirectiveRow, type CspRowIssue, allowsValue } from './csp-directives';
import { directiveNames } from './web-protection-form';

interface CspDirectiveFieldsProps {
    readonly index: number;
    readonly row: CspDirectiveRow;
    readonly issue?: CspRowIssue;
    readonly nameError?: string;
    readonly valueError?: string;
    readonly editable: boolean;
    readonly onChange: (patch: Partial<CspDirectiveRow>) => void;
    readonly onRemove: () => void;
}

export function CspDirectiveFields({ index, row, issue, nameError, valueError, editable, onChange, onRemove }: CspDirectiveFieldsProps) {
    const nameId = `csp-name-${index}`;
    const valueId = `csp-value-${index}`;
    return (
        <div className="flex items-start gap-3">
            <Field className="flex-1" data-invalid={!!nameError}>
                <FieldLabel htmlFor={nameId} className="flex items-center gap-1">
                    Name
                    {issue?.unknownName && (
                        <Tooltip>
                            <TooltipTrigger asChild>
                                <TriangleAlertIcon className="size-3.5 text-warning" aria-label="Unrecognized directive" />
                            </TooltipTrigger>
                            <TooltipContent>
                                This is not a directive we recognize. It will be saved as entered, so check the spelling.
                            </TooltipContent>
                        </Tooltip>
                    )}
                </FieldLabel>
                <Combobox
                    items={directiveNames(row.name)}
                    // Escape keeps the typed name.
                    value={row.name}
                    onValueChange={(name: string | null, details: { reason: string }) =>
                        details.reason !== 'escape-key' && onChange({ name: name ?? '' })
                    }
                    inputValue={row.name}
                    onInputValueChange={(name, details) => details.reason !== 'escape-key' && onChange({ name })}
                    filter={null}
                    disabled={!editable}
                >
                    <ComboboxInput
                        id={nameId}
                        placeholder="default-src"
                        aria-invalid={!!nameError}
                        aria-describedby={nameError ? `${nameId}-error` : undefined}
                        showTrigger={false}
                        disabled={!editable}
                    />
                    <ComboboxContent>
                        <ComboboxList>
                            {(name: string) => (
                                <ComboboxItem key={name} value={name}>
                                    {name}
                                </ComboboxItem>
                            )}
                        </ComboboxList>
                    </ComboboxContent>
                </Combobox>
                {nameError && <FieldError id={`${nameId}-error`}>{nameError}</FieldError>}
            </Field>
            <Field className="flex-1" data-invalid={!!valueError}>
                <FieldLabel htmlFor={valueId} className="flex items-center gap-1">
                    Value
                    {issue?.reportToNeedsEndpoint && (
                        <Tooltip>
                            <TooltipTrigger asChild>
                                <InfoIcon className="size-3.5 text-muted-foreground" aria-label="Reporting endpoint needed" />
                            </TooltipTrigger>
                            <TooltipContent>
                                report-to needs a Reporting-Endpoints header, which AM does not send. Add report-uri as well, or have a
                                proxy supply the header.
                            </TooltipContent>
                        </Tooltip>
                    )}
                </FieldLabel>
                <Input
                    id={valueId}
                    placeholder={allowsValue(row.name) ? "'self' https://cdn.example.com" : 'No value required'}
                    value={row.value}
                    disabled={!editable || !allowsValue(row.name)}
                    aria-invalid={!!valueError}
                    aria-describedby={valueError ? `${valueId}-error` : undefined}
                    onChange={event => onChange({ value: event.target.value })}
                />
                {valueError && <FieldError id={`${valueId}-error`}>{valueError}</FieldError>}
            </Field>
            {editable && (
                <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    className="mt-6"
                    aria-label={`Remove directive ${row.name || index + 1}`}
                    onClick={onRemove}
                >
                    <Trash2Icon aria-hidden />
                </Button>
            )}
        </div>
    );
}
