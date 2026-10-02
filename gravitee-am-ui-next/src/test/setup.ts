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
import '@testing-library/jest-dom/vitest';

class ResizeObserverMock {
    observe() {}
    unobserve() {}
    disconnect() {}
}
globalThis.ResizeObserver = globalThis.ResizeObserver ?? (ResizeObserverMock as unknown as typeof ResizeObserver);

// Radix calls these through Pointer Events; jsdom does not implement them.
Element.prototype.hasPointerCapture ??= (() => false) as Element['hasPointerCapture'];
Element.prototype.setPointerCapture ??= (() => undefined) as Element['setPointerCapture'];
Element.prototype.releasePointerCapture ??= (() => undefined) as Element['releasePointerCapture'];
Element.prototype.scrollIntoView ??= (() => undefined) as Element['scrollIntoView'];

Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
    })),
});
