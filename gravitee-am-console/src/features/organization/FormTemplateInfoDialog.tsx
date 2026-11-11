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
import { Button, Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@gravitee/graphene-core';
import { LOGIN_FORM_EXAMPLE } from './login-form-example';

interface FormTemplateInfoDialogProps {
    readonly open: boolean;
    readonly template: string;
    readonly phrase: string;
    readonly onOpenChange: (open: boolean) => void;
}

export function FormTemplateInfoDialog({ open, template, phrase, onOpenChange }: FormTemplateInfoDialogProps) {
    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-3xl">
                <DialogHeader>
                    <DialogTitle>Write your own {phrase} page</DialogTitle>
                    <DialogDescription>This is the minimal HTML element required to perform {phrase}.</DialogDescription>
                </DialogHeader>
                {template === 'LOGIN' && (
                    <pre className="max-h-96 overflow-auto rounded-md bg-muted p-3 font-mono text-sm">{LOGIN_FORM_EXAMPLE}</pre>
                )}
                <DialogFooter>
                    <Button variant="outline" onClick={() => onOpenChange(false)}>
                        Close
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
