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
import {Component, EventEmitter, Input, OnInit, Output} from "@angular/core";
import {TimeConverterService} from "../../../../../../../services/time-converter.service";
import moment from "moment";

@Component({
  selector: 'mfa-optional',
  templateUrl: './mfa-optional.component.html',
  styleUrls: ['./mfa-optional.component.scss']
})
export class MfaOptionalComponent implements OnInit {

  private humanTime: { skipTime: any; skipUnit: any };

  @Input() enrollment: any;
  @Output("settings-change") settingsChangeEmitter: EventEmitter<any> = new EventEmitter<any>();

  constructor(private timeConverterService: TimeConverterService) {
  }

  ngOnInit(): void {
    const time = this.enrollment && this.enrollment.skipTimeSeconds ? this.enrollment.skipTimeSeconds : 36000; // Default 10h
    this.humanTime = {
      'skipTime': this.timeConverterService.getTime(time),
      'skipUnit': this.timeConverterService.getUnitTime(time)
    };
  }

  displaySkipTime() {
    return this.humanTime.skipTime;
  }

  displaySkipUnit() {
    return this.humanTime.skipUnit;
  }

  onSkipTimeInEvent($event) {
    this.humanTime.skipTime = $event.target.value;
    this.updateOptionalEnrollement();
  }

  onSkipTimeUnitEvent($event) {
    this.humanTime.skipUnit = $event.value;
    this.updateOptionalEnrollement();
  }

  private updateOptionalEnrollement() {
    this.settingsChangeEmitter.emit({
      "forceEnrollment": false,
      "skipTimeSeconds": this.humanTimeToSeconds()
    });
  }

  private humanTimeToSeconds() {
    return moment.duration(this.humanTime.skipTime, this.humanTime.skipUnit).asSeconds();
  }
}
