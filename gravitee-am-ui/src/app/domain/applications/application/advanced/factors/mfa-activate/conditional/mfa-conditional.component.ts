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
  selector: 'mfa-conditional',
  templateUrl: './mfa-conditional.component.html',
  styleUrls: ['./mfa-conditional.component.scss']
})
export class MfaConditionalComponent {

  @Input() adaptiveMfaRule: string;
  @Output("on-rule-change") amfaRuleEmitter: EventEmitter<string> = new EventEmitter<string>();

  constructor(private dialog: MatDialog) {
  }

  openAMFADialog($event) {
    $event.preventDefault();
    this.dialog.open(AdaptiveMfaDialog, {width: '700px'});
  }

  updateAdaptiveMfaRule($event) {
    if($event.target){
      this.amfaRuleEmitter.emit($event.target.value);
    }
  }
}


@Component({
  selector: 'adaptive-mfa-dialog',
  templateUrl: './dialog/adaptive-mfa-info.component.html',
})
export class AdaptiveMfaDialog {
  constructor(public dialogRef: MatDialogRef<AdaptiveMfaDialog>) {
  }
}
