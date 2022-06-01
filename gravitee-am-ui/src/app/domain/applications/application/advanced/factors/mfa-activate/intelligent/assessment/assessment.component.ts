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

@Component({
  selector: 'assessment',
  templateUrl: './assessment.component.html',
  styleUrls: ['./assessment.component.scss']
})
export class AssessmentComponent implements OnInit {

  @Input() title:string;
  @Input() assessment: any;
  @Input() options: any[];
  @Output("on-assessment-change") assessmentChangeEmmit: EventEmitter<any> = new EventEmitter<any>();

  current: any;

  ngOnInit(): void {
    this.current = this.getCurrentOptions();
  }

  private getCurrentOptions() {
    if (this.assessment && this.assessment.enabled && this.assessment.thresholds) {
      const keys = Object.keys(this.assessment.thresholds);
      if (keys.length > 0) {
        const current = keys.map(key => this.options.find(option => option.value === key));
        if (current.length > 0){
          return current[0];
        }
      }
    }
    return this.options[0];
  }

  selectElement(assessment) {
    this.current = assessment;
    this.assessmentChangeEmmit.emit(assessment);
  }
}
