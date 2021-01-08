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
export type TimeRangeId = '1d' | '1h' | '12h' | '7d' | '30d' | '90d';

export type UnitOfTime = 'years'
  | 'months'
  | 'weeks'
  | 'days'
  | 'hours'
  | 'minutes'
  | 'seconds'
  | 'milliseconds'

export interface TimeRange {
  id: TimeRangeId,
  name: string,
  value: number,
  unit: UnitOfTime,
  interval: number
}

export const defaultTimeRangeId: TimeRangeId = '1d';

export const availableTimeRanges: TimeRange[] = [
  {
    id: '1h',
    name: 'Last hour',
    value: 1,
    unit: 'hours',
    interval: 1000 * 60,
  },
  {
    id: '12h',
    name: 'Last 12 hours',
    value: 12,
    unit: 'hours',
    interval: 1000 * 60 * 60,
  },
  {
    id: '1d',
    name: 'Today',
    value: 1,
    unit: 'days',
    interval: 1000 * 60 * 60,
  },
  {
    id: '7d',
    name: 'This week',
    value: 1,
    unit: 'weeks',
    interval: 1000 * 60 * 60 * 24,
  },
  {
    id: '30d',
    name: 'This month',
    value: 1,
    unit: 'months',
    interval: 1000 * 60 * 60 * 24,
  },
  {
    id: '90d',
    name: 'Last 90 days',
    value: 3,
    unit: 'months',
    interval: 1000 * 60 * 60 * 24,
  },
];
