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
import {Component, Inject, OnInit} from '@angular/core';
import {MatDialogRef} from '@angular/material/dialog';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';

@Component({
  selector: 'app-confirm',//TODO does this need to be unique to dictionary dialog?
  templateUrl: './dictionary-dialog.component.html',
  styleUrls: ['./dictionary-dialog.component.scss']
})
export class DictionaryDialog implements OnInit {

  public prop1: string;
  public prop2: string;
  public prop1Label: string;
  public prop2Label: string;
  public title: string;

  constructor(@Inject(MAT_DIALOG_DATA) public data: {title: string, prop1Label: string, prop2Label: string}, public dialogRef: MatDialogRef<DictionaryDialog>) {
    this.prop1Label = data.prop1Label;
    this.prop2Label = data.prop2Label;
    this.title = data.title;
    this.dialogRef = dialogRef;
  }

  ngOnInit() {
  }

  validate(){
    if ((this.prop1 && this.prop1.trim().length > 0) && (this.prop2 && this.prop2.trim().length > 0)) {
      this.dialogRef.close({prop1: this.prop1, prop2: this.prop2})
    }
  }
}
