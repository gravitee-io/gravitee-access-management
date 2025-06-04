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
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { duration } from 'moment';

import { TimeConverterService } from '../../../../../../services/time-converter.service';

@Component({
  selector: 'time-period-picker',
  templateUrl: './time-period-picker.component.html',
  styleUrls: ['./time-period-picker.component.scss'],
  standalone: false,
})
export class TimePeriodPickerComponent implements OnInit {
  private humanTime: { skipTime: any; skipUnit: any };

  @Input() defaultTimeSec: number;
  @Input() title: string;
  @Output() settingsChange = new EventEmitter<number>();

  constructor(private timeConverterService: TimeConverterService) {}

  ngOnInit(): void {
    const time = this.defaultTimeSec ? this.defaultTimeSec : 36000;
    this.humanTime = {
      skipTime: this.timeConverterService.getTime(time),
      skipUnit: this.timeConverterService.getUnitTime(time),
    };
    this.settingsChange.emit(this.humanTimeToSeconds());
  }

  displaySkipTime() {
    return this.humanTime.skipTime;
  }

  displaySkipUnit() {
    return this.humanTime.skipUnit;
  }

  onSkipTimeInEvent($event: any): void {
    this.humanTime.skipTime = $event.target.value;
    this.update();
  }

  onSkipTimeUnitEvent($event: any): void {
    this.humanTime.skipUnit = $event.value;
    this.update();
  }

  private update(): void {
    this.settingsChange.emit(this.humanTimeToSeconds());
  }

  private humanTimeToSeconds(): number {
    return duration(this.humanTime.skipTime, this.humanTime.skipUnit).asSeconds();
  }
}
