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
import {Component, EventEmitter, Input, Output} from "@angular/core";
import {MatDialog, MatDialogRef} from "@angular/material/dialog";

@Component({
  selector: 'mfa-step-up',
  templateUrl: './mfa-step-up.component.html',
  styleUrls: ['./mfa-step-up.component.scss']
})
export class MfaStepUpComponent {

  @Input() stepUpRule: string = "";
  @Output("on-rule-change") stepUpRuleEmitter: EventEmitter<string> = new EventEmitter<string>();

  constructor(
    private dialog: MatDialog
  ) {}

  change($event) {
    if($event.target){
      this.stepUpRuleEmitter.emit($event.target.value);
    }
  }

  openStepUpDialog($event) {
    $event.preventDefault();
    this.dialog.open(MfaStepUpDialog, {width: '700px'});
  }

}

@Component({
  selector: 'mfa-step-up-dialog',
  templateUrl: './dialog/mfa-step-up-info.component.html',
})
export class MfaStepUpDialog {
  constructor(public dialogRef: MatDialogRef<MfaStepUpDialog>) {
  }
}
