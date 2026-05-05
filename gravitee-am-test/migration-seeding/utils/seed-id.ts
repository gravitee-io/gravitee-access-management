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
 * Derives a deterministic, migration-seeding-identifiable name from a resource type,
 * the AM version being seeded, and a per-type sequence number.
 *
 * Example: seedName('domain', '4.11', 1) → 'am-seed-4-11-domain-1'
 *
 * All objects created by migration seeding carry this prefix so they are
 * unambiguously identifiable in any AM environment.
 */
export function seedName(type: string, version: string, n: number): string {
  const v = version.replace(/\./g, '-');
  return `am-seed-${v}-${type}-${n}`;
}
