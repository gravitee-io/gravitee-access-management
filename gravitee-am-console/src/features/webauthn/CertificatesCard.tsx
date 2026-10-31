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
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    Field,
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
    toast,
} from '@gravitee/graphene-core';
import { CheckIcon, PencilIcon, PlusIcon, Trash2Icon, XIcon } from '@gravitee/graphene-core/icons';
import { useState } from 'react';

export interface Certificate {
    readonly name: string;
    readonly value: string;
}

const ATTESTATION_NAMES = ['none', 'u2f', 'packed', 'android-key', 'android-safetynet', 'tpm', 'apple', 'mds'];

interface CertificatesCardProps {
    readonly certificates: readonly Certificate[];
    readonly canEdit: boolean;
    readonly onChange: (certificates: readonly Certificate[]) => void;
}

export function CertificatesCard({ certificates, canEdit, onChange }: CertificatesCardProps) {
    const [draft, setDraft] = useState<Certificate>({ name: '', value: '' });
    const [editing, setEditing] = useState<{ readonly index: number } & Certificate>();
    const exists = (name: string) => certificates.some(certificate => certificate.name === name);

    const add = () => {
        if (exists(draft.name)) {
            toast.error(`Error : metadata "${draft.name}" already exists`);
            return;
        }
        onChange([...certificates, draft]);
        setDraft({ name: '', value: '' });
    };

    const apply = () => {
        if (!editing?.name || !editing.value) {
            return;
        }
        if (editing.name !== certificates[editing.index].name && exists(editing.name)) {
            toast.error(`Error : attestation "${editing.name}" already exists`);
            return;
        }
        onChange(certificates.map((certificate, i) => (i === editing.index ? { name: editing.name, value: editing.value } : certificate)));
        setEditing(undefined);
    };

    return (
        <Card>
            <CardHeader>
                <CardTitle className="text-base">Certificates</CardTitle>
                <CardDescription>
                    Replace the default root certificates for an attestation type, to use a more recent one or a different cipher.
                </CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
                {canEdit && (
                    <div className="flex items-end gap-2">
                        <Field className="w-48">
                            <FieldLabel htmlFor="webauthn-cert-type">Type</FieldLabel>
                            <Select value={draft.name} onValueChange={name => setDraft(current => ({ ...current, name: name ?? '' }))}>
                                <SelectTrigger id="webauthn-cert-type" className="w-full">
                                    <SelectValue placeholder="Attestation" />
                                </SelectTrigger>
                                <SelectContent>
                                    {ATTESTATION_NAMES.map(name => (
                                        <SelectItem key={name} value={name}>
                                            {name}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </Field>
                        <Field className="flex-1">
                            <FieldLabel htmlFor="webauthn-cert-value">Certificate</FieldLabel>
                            <Input
                                id="webauthn-cert-value"
                                placeholder="PEM body, without the BEGIN / END lines"
                                value={draft.value}
                                onChange={event => setDraft(current => ({ ...current, value: event.target.value }))}
                            />
                        </Field>
                        <Button type="button" variant="outline" disabled={!draft.name || !draft.value} onClick={add}>
                            <PlusIcon aria-hidden />
                            Add
                        </Button>
                    </div>
                )}
                {certificates.length === 0 ? (
                    <p className="text-sm text-muted-foreground">There is no certificate. The default root certificates are used.</p>
                ) : (
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead className="w-48">Attestation</TableHead>
                                <TableHead>Certificate</TableHead>
                                <TableHead>
                                    <span className="sr-only">Actions</span>
                                </TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {certificates.map(({ name, value }, index) =>
                                editing?.index === index ? (
                                    <TableRow key={name}>
                                        <TableCell>
                                            <Input
                                                aria-label="Edited attestation name"
                                                value={editing.name}
                                                onChange={event => setEditing({ ...editing, name: event.target.value })}
                                                onKeyDown={event => event.key === 'Enter' && apply()}
                                            />
                                        </TableCell>
                                        <TableCell>
                                            <Input
                                                aria-label="Edited certificate"
                                                value={editing.value}
                                                onChange={event => setEditing({ ...editing, value: event.target.value })}
                                                onKeyDown={event => event.key === 'Enter' && apply()}
                                            />
                                        </TableCell>
                                        <TableCell className="text-right whitespace-nowrap">
                                            <Button
                                                type="button"
                                                variant="ghost"
                                                size="icon-xs"
                                                aria-label={`Apply the ${name} certificate changes`}
                                                disabled={!editing.name || !editing.value}
                                                onClick={apply}
                                            >
                                                <CheckIcon aria-hidden />
                                            </Button>
                                            <Button
                                                type="button"
                                                variant="ghost"
                                                size="icon-xs"
                                                aria-label={`Cancel the ${name} certificate changes`}
                                                onClick={() => setEditing(undefined)}
                                            >
                                                <XIcon aria-hidden />
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ) : (
                                    <TableRow key={name}>
                                        <TableCell className="font-medium">{name}</TableCell>
                                        <TableCell>
                                            <span className="block max-w-md truncate font-mono text-xs" title={value}>
                                                {value}
                                            </span>
                                        </TableCell>
                                        <TableCell className="text-right whitespace-nowrap">
                                            {canEdit && (
                                                <>
                                                    <Button
                                                        type="button"
                                                        variant="ghost"
                                                        size="icon-xs"
                                                        aria-label={`Edit ${name} certificate`}
                                                        onClick={() => setEditing({ index, name, value })}
                                                    >
                                                        <PencilIcon aria-hidden />
                                                    </Button>
                                                    <Button
                                                        type="button"
                                                        variant="ghost"
                                                        size="icon-xs"
                                                        aria-label={`Remove ${name} certificate`}
                                                        onClick={() => {
                                                            onChange(certificates.filter((_, i) => i !== index));
                                                            setEditing(undefined);
                                                        }}
                                                    >
                                                        <Trash2Icon aria-hidden />
                                                    </Button>
                                                </>
                                            )}
                                        </TableCell>
                                    </TableRow>
                                ),
                            )}
                        </TableBody>
                    </Table>
                )}
            </CardContent>
        </Card>
    );
}
