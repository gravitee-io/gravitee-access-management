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
import { Button, FieldError, Input } from '@gravitee/graphene-core';
import { PlusIcon, XIcon } from '@gravitee/graphene-core/icons';
import { useState } from 'react';
import type { Attribute } from './user-helpers';

interface AttributesEditorProps {
    readonly attributes: readonly Attribute[];
    readonly editable: boolean;
    readonly onChange: (attributes: Attribute[]) => void;
}

export function AttributesEditor({ attributes, editable, onChange }: AttributesEditorProps) {
    const update = (index: number, patch: Partial<Attribute>) =>
        onChange(attributes.map((attribute, i) => (i === index ? { ...attribute, ...patch } : attribute)));
    // A field shows its error once it has lost focus, and hides it while it is edited.
    const [focused, setFocused] = useState<string | null>(null);
    const [blurred, setBlurred] = useState<ReadonlySet<string>>(new Set());
    const leave = (field: string) => {
        setFocused(null);
        setBlurred(current => new Set(current).add(field));
    };
    const remove = (index: number) => {
        onChange(attributes.filter((_, i) => i !== index));
        setBlurred(new Set());
    };
    const showError = (field: string, fromStart: boolean) => focused !== field && (fromStart || blurred.has(field));

    return (
        <div className="flex flex-col gap-2">
            {attributes.length === 0 && <p className="text-sm text-muted-foreground">No additional information.</p>}
            {attributes.map((attribute, index) => {
                const nameError = attribute.name === '' && showError(`${index}-name`, false) ? 'Enter a name' : undefined;
                const valueError =
                    !attribute.readOnly && attribute.value === '' && showError(`${index}-value`, attribute.stored)
                        ? 'Enter a value'
                        : undefined;
                return (
                    <div key={index} className="flex items-start gap-2">
                        <div className="flex w-48 flex-col gap-1">
                            <Input
                                aria-label="Attribute name"
                                placeholder="Name"
                                value={attribute.name}
                                disabled={!editable || attribute.stored}
                                aria-invalid={Boolean(nameError)}
                                aria-describedby={nameError ? `attribute-${index}-name-error` : undefined}
                                onFocus={() => setFocused(`${index}-name`)}
                                onBlur={() => leave(`${index}-name`)}
                                onChange={event => update(index, { name: event.target.value })}
                            />
                            {nameError && <FieldError id={`attribute-${index}-name-error`}>{nameError}</FieldError>}
                        </div>
                        <div className="flex flex-1 flex-col gap-1">
                            <Input
                                aria-label={`Value of ${attribute.name || 'attribute'}`}
                                placeholder="Value"
                                value={attribute.value}
                                disabled={!editable || attribute.readOnly}
                                aria-invalid={Boolean(valueError)}
                                aria-describedby={valueError ? `attribute-${index}-value-error` : undefined}
                                onFocus={() => setFocused(`${index}-value`)}
                                onBlur={() => leave(`${index}-value`)}
                                onChange={event => update(index, { value: event.target.value })}
                                className={attribute.readOnly ? 'font-mono' : undefined}
                            />
                            {valueError && <FieldError id={`attribute-${index}-value-error`}>{valueError}</FieldError>}
                        </div>
                        {editable && (
                            <Button
                                variant="ghost"
                                size="icon-sm"
                                type="button"
                                aria-label={`Remove ${attribute.name || 'attribute'}`}
                                onClick={() => remove(index)}
                            >
                                <XIcon aria-hidden />
                            </Button>
                        )}
                    </div>
                );
            })}
            {editable && (
                <Button
                    variant="outline"
                    size="sm"
                    type="button"
                    className="self-start"
                    onClick={() => onChange([...attributes, { name: '', value: '', stored: false, readOnly: false }])}
                >
                    <PlusIcon aria-hidden />
                    Add attribute
                </Button>
            )}
        </div>
    );
}
