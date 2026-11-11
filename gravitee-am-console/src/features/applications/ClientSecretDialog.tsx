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
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    Field,
    FieldDescription,
    FieldLabel,
    Input,
    toast,
} from '@gravitee/graphene-core';
import { CopyIcon } from '@gravitee/graphene-core/icons';
import { useState } from 'react';
import { copyText } from '../../lib/clipboard';

interface ClientSecretDialogProps {
    /** The dialog is open while it is set. */
    readonly secret: string | null;
    readonly onDone: () => void;
}

/** The secret shows once. The button unlocks only after a copy. */
export function ClientSecretDialog({ secret, onDone }: ClientSecretDialogProps) {
    const [copied, setCopied] = useState(false);
    const markCopied = () => {
        setCopied(true);
        toast.success('Client Secret copied to the clipboard');
    };

    return (
        <Dialog open={secret !== null}>
            <DialogContent
                showCloseButton={false}
                onInteractOutside={event => event.preventDefault()}
                onEscapeKeyDown={event => event.preventDefault()}
            >
                <DialogHeader>
                    <DialogTitle>Copy your client secret</DialogTitle>
                    <DialogDescription>
                        This is the only time the secret is shown. Store it somewhere safe before you continue — you can renew it later, but
                        not display it again.
                    </DialogDescription>
                </DialogHeader>
                <Field>
                    <FieldLabel htmlFor="client-secret">Client secret</FieldLabel>
                    <div className="flex gap-2">
                        <Input
                            id="client-secret"
                            readOnly
                            value={secret ?? ''}
                            className="font-mono"
                            onCopy={event => {
                                if (event.currentTarget.selectionStart !== event.currentTarget.selectionEnd) markCopied();
                            }}
                        />
                        <Button
                            type="button"
                            variant="outline"
                            size="icon"
                            aria-label="Copy to clipboard"
                            onClick={() =>
                                void copyText(secret ?? '').then(markCopied, () =>
                                    toast.error('Could not copy to the clipboard. Select the secret and copy it.'),
                                )
                            }
                        >
                            <CopyIcon aria-hidden />
                        </Button>
                    </div>
                    {!copied && <FieldDescription>Copy your client secret before progressing.</FieldDescription>}
                </Field>
                <DialogFooter>
                    <Button onClick={onDone} disabled={!copied}>
                        I have copied the secret
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
