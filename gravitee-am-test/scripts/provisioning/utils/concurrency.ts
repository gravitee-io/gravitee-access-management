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
/**
 * Run an array of async tasks with a fixed concurrency limit and preserve result order.
 * @param items Items to process
 * @param limit Maximum number of concurrent workers
 * @param worker Async worker function invoked with (item, index)
 * @returns Array of results, in the same order as input items
 */
export async function runWithConcurrency<I, O>(items: I[], limit: number, worker: (item: I, index: number) => Promise<O>): Promise<O[]> {
  const results: O[] = new Array(items.length) as O[];
  let idx = 0;
  async function next(): Promise<void> {
    const current = idx++;
    if (current >= items.length) return;
    results[current] = await worker(items[current], current);
    return next();
  }
  const starters = Array.from({ length: Math.min(limit, items.length) }, () => next());
  await Promise.all(starters);
  return results;
}
