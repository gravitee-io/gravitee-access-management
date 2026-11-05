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
    Alert,
    AlertDescription,
    AlertTitle,
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
    FileUpload,
    type FileUploadItemValue,
    JsonSchemaForm,
    jsonSchemaResolver,
    toast,
} from '@gravitee/graphene-core';
import { type UseQueryResult, useQueries } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { type Control, type FieldErrors, type FieldValues, type Path, type PathValue, type Resolver, useController } from 'react-hook-form';
import { http } from '../../lib/api/http';
import { type AdaptedSchema, adaptAmSchema, type SchemaFile, type SchemaResource, type SchemaStringList } from '../../lib/forms/am-schema';
import { TagInput } from './TagInput';

const REQUIRED_MESSAGE = 'This field is required';

/** The file as JSON, with its content in base64. */
export async function fileValue(file: File): Promise<string> {
    const dataUrl = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result as string);
        reader.onerror = () => reject(reader.error);
        reader.readAsDataURL(file);
    });
    return JSON.stringify({ name: file.name, type: file.type, size: file.size, content: dataUrl.split(',')[1] });
}

function combineDictionaries(results: UseQueryResult<string[]>[]) {
    return {
        data: results.map(result => result.data),
        pending: results.some(result => result.isPending),
    };
}

/** `adapted` stays undefined until the dictionaries load. A dictionary that fails to load gives an empty list. `resources` must be stable across renders. */
export function useAdaptedSchema(
    schema: Record<string, unknown> | undefined,
    resources?: readonly SchemaResource[],
): { readonly adapted?: AdaptedSchema } {
    const endpoints = useMemo(() => (schema ? adaptAmSchema(schema).dictionaryEndpoints : []), [schema]);
    const dictionaries = useQueries({
        queries: endpoints.map(endpoint => ({
            queryKey: ['plugin-schema-dictionary', endpoint],
            queryFn: () => http<string[]>(endpoint),
            staleTime: Infinity,
        })),
        combine: combineDictionaries,
    });
    const adapted = useMemo(
        () =>
            schema && !dictionaries.pending
                ? adaptAmSchema(schema, {
                      resources,
                      dictionaries: Object.fromEntries(endpoints.map((endpoint, i) => [endpoint, dictionaries.data[i] ?? []])),
                  })
                : undefined,
        [schema, resources, endpoints, dictionaries],
    );
    return { adapted };
}

type ErrorTree = Record<string, unknown>;

function isFieldError(node: unknown): boolean {
    return typeof (node as ErrorTree).type === 'string';
}

/** The first error of a field wins. */
function mergeErrors(target: ErrorTree, source: ErrorTree): ErrorTree {
    const merged = { ...target };
    for (const [key, value] of Object.entries(source)) {
        const existing = merged[key];
        merged[key] =
            existing && !isFieldError(existing) && !isFieldError(value)
                ? mergeErrors(existing as ErrorTree, value as ErrorTree)
                : (existing ?? value);
    }
    return merged;
}

/** Adds the conditional checks and the required check of the file fields, which the schema does not hold. `basePath` must be a top-level key. */
export function pluginSchemaResolver<T extends Record<string, unknown>>(adapted: AdaptedSchema, basePath: string): Resolver<T> {
    const resolvers = [adapted.schema, ...adapted.conditions].map(schema => jsonSchemaResolver<T>(schema, { basePath }));
    return async (values, context, options) => {
        const results = await Promise.all(resolvers.map(resolver => resolver(values, context, options)));
        const configuration = (values[basePath] ?? {}) as Record<string, unknown>;
        const fileErrors = Object.fromEntries(
            [
                ...adapted.files.filter(file => file.required && !configuration[file.key]),
                ...adapted.stringLists.filter(list => {
                    const value = configuration[list.key];
                    return list.required && (!Array.isArray(value) || value.length === 0);
                }),
            ].map(field => [field.key, { type: 'required', message: REQUIRED_MESSAGE }]),
        );
        const errors = results.reduce((merged, result) => mergeErrors(merged, result.errors), { [basePath]: fileErrors } as ErrorTree);
        const configurationErrors = errors[basePath] as ErrorTree;
        // A failed `if`/`then` block also reports "must match then" at the root. Its field errors already say what is wrong.
        if ((configurationErrors.root as ErrorTree | undefined)?.type === 'if') {
            delete configurationErrors.root;
        }
        if (Object.keys(configurationErrors).length === 0) {
            delete errors[basePath];
        }
        return Object.keys(errors).length === 0 ? { values, errors: {} } : { values: {}, errors: errors as FieldErrors<T> };
    };
}

function SchemaFileField<T extends FieldValues>({
    file,
    control,
    name,
    disabled,
}: {
    readonly file: SchemaFile;
    readonly control: Control<T>;
    readonly name: string;
    readonly disabled?: boolean;
}) {
    const { field, fieldState } = useController({ control, name: `${name}.${file.key}` as Path<T> });
    const [pick, setPick] = useState<{ readonly item: FileUploadItemValue; readonly value: string; readonly previous: unknown }>();
    // A form reset changes the value, so the picked file no longer shows.
    const picked = pick && pick.value === field.value ? pick.item : undefined;
    const saved = !picked && typeof field.value === 'string' && field.value ? field.value.split('/').pop() : undefined;

    return (
        <div className="flex flex-col gap-2">
            <FileUpload
                label={
                    file.required ? (
                        <span className="inline-flex items-baseline gap-0.5">
                            {file.title ?? file.key}
                            <span aria-hidden="true">*</span>
                        </span>
                    ) : (
                        (file.title ?? file.key)
                    )
                }
                hint={file.description}
                disabled={disabled}
                maxFiles={1}
                items={picked ? [picked] : []}
                invalid={fieldState.invalid}
                errors={fieldState.error?.message ? [fieldState.error.message] : undefined}
                onFilesAdd={async ([added]) => {
                    try {
                        const previous = picked && pick ? pick.previous : field.value;
                        const value = await fileValue(added);
                        field.onChange(value);
                        setPick({ item: { id: added.name, file: added, status: 'success' }, value, previous });
                    } catch {
                        toast.error(`Could not read the file ${added.name}.`);
                    }
                }}
                // Remove goes back to the saved file.
                onFileRemove={() => {
                    setPick(undefined);
                    field.onChange(pick?.previous);
                }}
            />
            {saved && <FieldDescription>File currently saved: {saved}</FieldDescription>}
        </div>
    );
}

function SchemaStringListField<T extends FieldValues>({
    list,
    control,
    name,
    disabled,
}: {
    readonly list: SchemaStringList;
    readonly control: Control<T>;
    readonly name: string;
    readonly disabled?: boolean;
}) {
    // An unset list registers as [], as the Graphene array field does.
    const { field, fieldState } = useController({
        control,
        name: `${name}.${list.key}` as Path<T>,
        defaultValue: [] as PathValue<T, Path<T>>,
    });
    const id = `plugin-${list.key}`;
    const values = Array.isArray(field.value) ? (field.value as string[]) : [];

    return (
        <Field data-invalid={fieldState.invalid}>
            <FieldLabel htmlFor={id}>
                {list.title ?? list.key}
                {list.required && <span aria-hidden="true">*</span>}
            </FieldLabel>
            <TagInput
                id={id}
                values={values}
                disabled={disabled || list.readOnly}
                addOnBlur
                invalid={fieldState.invalid}
                describedBy={fieldState.error ? `${id}-error` : undefined}
                onBlur={field.onBlur}
                onChange={field.onChange}
                onDuplicate={value => toast.error(`"${value}" is already in the list.`)}
            />
            {list.description && <FieldDescription>{list.description}</FieldDescription>}
            {fieldState.error && <FieldError id={`${id}-error`} errors={[fieldState.error]} />}
        </Field>
    );
}

interface PluginSchemaFormProps<T extends FieldValues> {
    readonly adapted: AdaptedSchema;
    readonly control: Control<T>;
    /** Must match the `basePath` of `pluginSchemaResolver`. */
    readonly name: string;
    readonly disabled?: boolean;
}

export function PluginSchemaForm<T extends FieldValues>({ adapted, control, name, disabled }: PluginSchemaFormProps<T>) {
    return (
        <div className="flex flex-col gap-4">
            {import.meta.env.DEV && adapted.unsupported.length > 0 && (
                <Alert variant="warning">
                    <AlertTitle>Fields shown without their Angular widget or condition</AlertTitle>
                    <AlertDescription>{adapted.unsupported.join(', ')}</AlertDescription>
                </Alert>
            )}
            {adapted.files.map(file => (
                <SchemaFileField key={file.key} file={file} control={control} name={name} disabled={disabled} />
            ))}
            <JsonSchemaForm schema={adapted.schema} control={control as unknown as Control} name={name} disabled={disabled} />
            {adapted.stringLists.map(list => (
                <SchemaStringListField key={list.key} list={list} control={control} name={name} disabled={disabled} />
            ))}
        </div>
    );
}
