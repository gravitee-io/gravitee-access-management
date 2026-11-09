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
import { Button, Input } from '@gravitee/graphene-core';
import { PlusIcon, XIcon } from '@gravitee/graphene-core/icons';
import type { Attribute } from './user-helpers';

interface AttributesEditorProps {
    readonly attributes: readonly Attribute[];
    readonly editable: boolean;
    readonly onChange: (attributes: Attribute[]) => void;
}

/** The Angular "(+) add user attribute" rows: custom claims in the user's `additionalInformation`. */
export function AttributesEditor({ attributes, editable, onChange }: AttributesEditorProps) {
    const update = (index: number, patch: Partial<Attribute>) =>
        onChange(attributes.map((attribute, i) => (i === index ? { ...attribute, ...patch } : attribute)));

    return (
        <div className="flex flex-col gap-2">
            {attributes.length === 0 && <p className="text-sm text-muted-foreground">No additional information.</p>}
            {attributes.map((attribute, index) => (
                <div key={index} className="flex items-center gap-2">
                    <Input
                        aria-label="Attribute name"
                        placeholder="Name"
                        value={attribute.name}
                        disabled={!editable || attribute.stored}
                        onChange={event => update(index, { name: event.target.value })}
                        className="w-48"
                    />
                    <Input
                        aria-label={`Value of ${attribute.name || 'attribute'}`}
                        placeholder="Value"
                        value={attribute.value}
                        disabled={!editable || attribute.readOnly}
                        onChange={event => update(index, { value: event.target.value })}
                        className={attribute.readOnly ? 'font-mono' : undefined}
                    />
                    {editable && (
                        <Button
                            variant="ghost"
                            size="icon-sm"
                            type="button"
                            aria-label={`Remove ${attribute.name || 'attribute'}`}
                            onClick={() => onChange(attributes.filter((_, i) => i !== index))}
                        >
                            <XIcon aria-hidden />
                        </Button>
                    )}
                </div>
            ))}
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
