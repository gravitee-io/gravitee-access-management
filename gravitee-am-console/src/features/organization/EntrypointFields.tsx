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
    Badge,
    Button,
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
    Input,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@gravitee/graphene-core';
import { XIcon } from '@gravitee/graphene-core/icons';
import type { Tag } from '../../lib/api/tags';

export type EntrypointDraft = {
    readonly name: string;
    readonly description: string;
    readonly url: string;
    readonly tags: readonly string[];
};

const URL_PATTERN = /^https?:\/\/.+/;

export function isEntrypointValid(draft: EntrypointDraft): boolean {
    return draft.name.trim() !== '' && URL_PATTERN.test(draft.url);
}

interface EntrypointFieldsProps {
    readonly draft: EntrypointDraft;
    readonly onChange: (patch: Partial<EntrypointDraft>) => void;
    /** Without a tag list, the sharding tags field is not shown. */
    readonly tags?: readonly Tag[];
    readonly readOnly?: boolean;
    /** The default entrypoint keeps its name, description and tags. */
    readonly lockIdentity?: boolean;
}

export function EntrypointFields({ draft, onChange, tags, readOnly = false, lockIdentity = false }: EntrypointFieldsProps) {
    const identityLocked = readOnly || lockIdentity;
    const urlInvalid = draft.url !== '' && !URL_PATTERN.test(draft.url);
    const selected = draft.tags.map(id => ({ id, name: tags?.find(tag => tag.id === id)?.name }));
    const available = (tags ?? []).filter(tag => !draft.tags.includes(tag.id));
    return (
        <div className="grid gap-5">
            <Field>
                <FieldLabel htmlFor="entrypoint-name">Name</FieldLabel>
                <Input
                    id="entrypoint-name"
                    value={draft.name}
                    readOnly={readOnly}
                    disabled={lockIdentity}
                    required
                    onChange={event => onChange({ name: event.target.value })}
                />
                <FieldDescription>A name for the entrypoint.</FieldDescription>
            </Field>
            <Field>
                <FieldLabel htmlFor="entrypoint-description">Description</FieldLabel>
                <Input
                    id="entrypoint-description"
                    value={draft.description}
                    readOnly={readOnly}
                    disabled={lockIdentity}
                    onChange={event => onChange({ description: event.target.value })}
                />
                <FieldDescription>A description for the entrypoint.</FieldDescription>
            </Field>
            <Field data-invalid={urlInvalid}>
                <FieldLabel htmlFor="entrypoint-url">Entrypoint url</FieldLabel>
                <Input
                    id="entrypoint-url"
                    value={draft.url}
                    readOnly={readOnly}
                    required
                    aria-invalid={urlInvalid}
                    aria-describedby={urlInvalid ? 'entrypoint-url-error' : undefined}
                    onChange={event => onChange({ url: event.target.value })}
                />
                {urlInvalid ? (
                    <FieldError id="entrypoint-url-error">The url must start with http:// or https://.</FieldError>
                ) : (
                    <FieldDescription>An url for the entrypoint.</FieldDescription>
                )}
            </Field>
            {tags && (
                <Field>
                    <FieldLabel htmlFor="entrypoint-tags">Sharding tags</FieldLabel>
                    {!identityLocked && (
                        <Select
                            value=""
                            disabled={available.length === 0}
                            onValueChange={id => id && onChange({ tags: [...draft.tags, id] })}
                        >
                            <SelectTrigger id="entrypoint-tags" className="w-full">
                                <SelectValue
                                    placeholder={available.length === 0 ? 'No other sharding tags available' : 'Add a sharding tag'}
                                />
                            </SelectTrigger>
                            <SelectContent>
                                {available.map(tag => (
                                    <SelectItem key={tag.id} value={tag.id}>
                                        {tag.id} | {tag.name}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    )}
                    {selected.length === 0 ? (
                        <FieldDescription>No sharding tags.</FieldDescription>
                    ) : (
                        <div className="flex flex-wrap gap-2">
                            {selected.map(tag => (
                                <Badge key={tag.id} variant="secondary">
                                    {tag.name ? `${tag.id} | ${tag.name}` : tag.id}
                                    {!identityLocked && (
                                        <Button
                                            type="button"
                                            variant="ghost"
                                            size="icon-xs"
                                            aria-label={`Remove sharding tag ${tag.id}`}
                                            onClick={() => onChange({ tags: draft.tags.filter(id => id !== tag.id) })}
                                        >
                                            <XIcon aria-hidden />
                                        </Button>
                                    )}
                                </Badge>
                            ))}
                        </div>
                    )}
                </Field>
            )}
        </div>
    );
}
