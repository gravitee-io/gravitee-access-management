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
import { Button } from '@gravitee/graphene-core';
import { useLayoutEffect, useRef, useState } from 'react';

interface SaveBarProps {
    /** The id of the `<form>` that Save submits. */
    readonly formId: string;
    readonly dirty: boolean;
    readonly saving: boolean;
    readonly invalid?: boolean;
    readonly onDiscard: () => void;
}

interface Box {
    readonly left: number;
    readonly width: number;
}

/** Floats at the bottom of the content column while `dirty` is true, so the page does not move when it appears. */
export function SaveBar({ formId, dirty, saving, invalid = false, onDiscard }: SaveBarProps) {
    // Absolute, so it takes no room or flex gap. Its parent gives the column the fixed bar lines up with.
    const anchor = useRef<HTMLSpanElement>(null);
    const [box, setBox] = useState<Box>();

    useLayoutEffect(() => {
        const column = anchor.current?.parentElement;
        if (!dirty || !column) {
            setBox(undefined);
            return undefined;
        }
        const measure = () => {
            const { left, width } = column.getBoundingClientRect();
            setBox(current => (current?.left === left && current.width === width ? current : { left, width }));
        };
        measure();
        const observer = new ResizeObserver(measure);
        observer.observe(column);
        window.addEventListener('resize', measure);
        return () => {
            observer.disconnect();
            window.removeEventListener('resize', measure);
        };
    }, [dirty]);

    if (!dirty) {
        return null;
    }
    return (
        <>
            <span ref={anchor} aria-hidden className="absolute" />
            {box && (
                <div
                    role="region"
                    aria-label="Unsaved changes"
                    className="fixed bottom-4 z-20 flex items-center justify-between gap-4 rounded-lg border border-border bg-card px-4 py-3 shadow-lg motion-safe:animate-save-bar-in"
                    style={{ left: box.left, width: box.width }}
                >
                    <p className="text-sm">You have unsaved changes.</p>
                    <div className="flex gap-2">
                        <Button type="button" variant="outline" disabled={saving} onClick={onDiscard}>
                            Discard
                        </Button>
                        <Button type="submit" form={formId} disabled={saving || invalid}>
                            {saving ? 'Saving…' : 'Save'}
                        </Button>
                    </div>
                </div>
            )}
        </>
    );
}
