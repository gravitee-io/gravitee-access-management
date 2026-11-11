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
import { Button, Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, Field } from '@gravitee/graphene-core';
import { CodeEditor } from '@gravitee/graphene-core/code-editor';
import type { Monaco, OnMount } from '@monaco-editor/react';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { getSpelGrammar } from '../../lib/api/management-api';
import { spelCompletions } from './idp-spel-completion';

type StandaloneEditor = Parameters<OnMount>[0];

interface SelectionRuleDialogProps {
    readonly providerName: string;
    readonly rule: string;
    readonly readOnly: boolean;
    readonly onApply: (rule: string) => void;
    readonly onClose: () => void;
}

export function SelectionRuleDialog({ providerName, rule, readOnly, onApply, onClose }: SelectionRuleDialogProps) {
    const [value, setValue] = useState(rule);
    const [mounted, setMounted] = useState<{ editor: StandaloneEditor; monaco: Monaco } | null>(null);
    const grammar = useQuery({ queryKey: ['spel-grammar'], queryFn: getSpelGrammar, staleTime: Infinity, enabled: !readOnly });

    useEffect(() => {
        if (!mounted || !grammar.data) {
            return;
        }
        const { editor, monaco } = mounted;
        const loaded = grammar.data;
        // A completion provider is global to the language, so it answers only for the model of this editor.
        const provider = monaco.languages.registerCompletionItemProvider('plaintext', {
            triggerCharacters: ['#', '.', '[', "'"],
            provideCompletionItems: (model, position) => {
                if (model !== editor.getModel()) {
                    return { suggestions: [] };
                }
                const text = model.getValueInRange({
                    startLineNumber: position.lineNumber,
                    startColumn: 1,
                    endLineNumber: position.lineNumber,
                    endColumn: position.column,
                });
                const { prefix, items } = spelCompletions(loaded, text);
                const range = {
                    startLineNumber: position.lineNumber,
                    startColumn: position.column - prefix.length,
                    endLineNumber: position.lineNumber,
                    endColumn: position.column,
                };
                return {
                    suggestions: items.map(item => ({
                        label: item.label,
                        insertText: item.insertText,
                        detail: item.detail,
                        kind: monaco.languages.CompletionItemKind.Property,
                        range,
                    })),
                };
            },
        });
        return () => provider.dispose();
    }, [mounted, grammar.data]);

    return (
        <Dialog open onOpenChange={open => !open && onClose()}>
            <DialogContent className="max-w-[calc(100%-2rem)] sm:max-w-2xl">
                <DialogHeader>
                    <DialogTitle>Selection rule — {providerName}</DialogTitle>
                    <DialogDescription>
                        An Expression Language condition. When it is true, the user is sent to this identity provider.
                    </DialogDescription>
                </DialogHeader>
                <Field role="group" aria-labelledby="selection-rule-label">
                    <p id="selection-rule-label" className="text-sm font-medium">
                        Rule
                    </p>
                    <CodeEditor
                        value={value}
                        onChange={next => setValue(next ?? '')}
                        language="plaintext"
                        readOnly={readOnly}
                        height={120}
                        options={{ ariaLabel: 'Rule', minimap: { enabled: false }, lineNumbers: 'off', wordWrap: 'on' }}
                        onMount={(editor, monaco) => setMounted({ editor, monaco })}
                    />
                </Field>
                <DialogFooter>
                    <Button type="button" variant="ghost" onClick={onClose}>
                        {readOnly ? 'Close' : 'Cancel'}
                    </Button>
                    {!readOnly && (
                        <Button type="button" onClick={() => onApply(value)}>
                            Apply
                        </Button>
                    )}
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
