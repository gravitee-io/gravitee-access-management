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
import { InputGroup, InputGroupAddon, InputGroupInput } from '@gravitee/graphene-core';
import { SearchIcon } from '@gravitee/graphene-core/icons';
import { useEffect, useRef } from 'react';

const DEBOUNCE_MS = 300;

interface SearchFieldProps {
    /** The plural noun of the list, as in "Search scopes". */
    readonly label: string;
    readonly onSearch: (query: string) => void;
}

/** Calls `onSearch` with the trimmed text 300 ms after the last change. */
export function SearchField({ label, onSearch }: SearchFieldProps) {
    const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
    useEffect(() => () => clearTimeout(timer.current), []);

    return (
        <InputGroup className="h-8 w-64 max-w-full">
            <InputGroupAddon align="inline-start">
                <SearchIcon aria-hidden />
            </InputGroupAddon>
            <InputGroupInput
                type="search"
                placeholder={`Search ${label}...`}
                aria-label={`Search ${label}`}
                onChange={event => {
                    const query = event.target.value.trim();
                    clearTimeout(timer.current);
                    timer.current = setTimeout(() => onSearch(query), DEBOUNCE_MS);
                }}
            />
        </InputGroup>
    );
}
