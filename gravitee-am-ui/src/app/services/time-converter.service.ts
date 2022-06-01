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
import {Injectable} from "@angular/core";
import moment, {DurationInputArg1, DurationInputArg2} from "moment";

@Injectable()
export class TimeConverterService {

  getTime(value: DurationInputArg1, unit: DurationInputArg2 = 'seconds') {
    if (value) {
      const humanizeDate = moment.duration(value, unit).humanize().split(' ');
      return (humanizeDate.length === 2)
        ? (humanizeDate[0] === 'a' || humanizeDate[0] === 'an') ? 1 : humanizeDate[0]
        : value;
    }
    return null;
  }

  getUnitTime(value: DurationInputArg1, unit: DurationInputArg2 = 'seconds') {
    if (value) {
      const humanizeDate = moment.duration(value, unit).humanize().split(' ');
      return (humanizeDate.length === 2)
        ? humanizeDate[1].endsWith('s') ? humanizeDate[1] : humanizeDate[1] + 's'
        : humanizeDate[2].endsWith('s') ? humanizeDate[2] : humanizeDate[2] + 's';
    }
    return 'seconds'
  }
}
