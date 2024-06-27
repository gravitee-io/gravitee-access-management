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
import { Injectable } from '@angular/core';
import moment, { DurationInputArg1, DurationInputArg2 } from 'moment';
import { duration } from 'moment/moment';

@Injectable()
export class TimeConverterService {
  getTime(value: DurationInputArg1, unit: DurationInputArg2 = 'seconds') {
    if (value) {
      return this.getExpiresIn(value, unit);
    }
    return null;
  }

  getUnitTime(value: DurationInputArg1, unit: DurationInputArg2 = 'seconds'): string {
    if (value) {
      const humanizeDate = moment.duration(value, unit).humanize().split(' ');
      return this.extractUnitTime(humanizeDate);
    }
    return 'seconds';
  }

  getHumanTime(value: DurationInputArg1, unit: DurationInputArg2 = 'seconds'): string {
    return moment.duration(value, unit).humanize();
  }

  private extractUnitTime(humanizeDate: string[]): string {
    const index = humanizeDate.length === 2 ? 1 : 2;
    return humanizeDate[index].endsWith('s') ? humanizeDate[index] : humanizeDate[index] + 's';
  }

  private getExpiresIn(value, unit: DurationInputArg2 = 'seconds') {
    const humanizeDate = duration(value, unit).humanize().split(' ');
    if (humanizeDate.length === 2) {
      return humanizeDate[0] === 'a' || humanizeDate[0] === 'an' ? 1 : humanizeDate[0];
    } else {
      return value;
    }
  }
}
