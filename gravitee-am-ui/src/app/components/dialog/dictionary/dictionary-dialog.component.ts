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
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {FormControl} from "@angular/forms";
import {Observable} from "rxjs";
import {map, startWith} from 'rxjs/operators';

interface Language {
  code: string;
  name: string;
}

@Component({
  selector: 'app-dictionary-dialog',
  templateUrl: './dictionary-dialog.component.html',
  styleUrls: ['./dictionary-dialog.component.scss']
})
export class DictionaryDialog implements OnInit {

  prop1: string;
  prop2: string;
  prop1Label: string;
  prop2Label: string;
  title: string;
  languageCodes: Language[];
  selectedCode = '123';
  languageCtrl = new FormControl('');
  filtered: Observable<Language[]>;


  constructor(@Inject(MAT_DIALOG_DATA) public data: { title: string, prop1Label: string, prop2Label: string, languageCodes: string[] }, public dialogRef: MatDialogRef<DictionaryDialog>) {
    this.prop1Label = data.prop1Label;
    this.prop2Label = data.prop2Label;
    this.title = data.title;
    this.dialogRef = dialogRef;
    this.languageCodes = [];
    if (data.languageCodes) {
      for (const [key, value] of data.languageCodes) {
        this.languageCodes.push({code: key, name: value});
      }
    }
  }

  ngOnInit() {
    this.filtered = this.languageCtrl.valueChanges.pipe(
      startWith(''),
      map(value => this._filter(value || "")),
    );
  }

  private _filter(value: any): Language[] {
    const filterValue = typeof value === "string" ? value.toLowerCase() : value.code.toLowerCase();
    return this.languageCodes.filter(lang => lang.code.includes(filterValue));
  }

  validate() {
    if ((this.prop1 && this.prop1.trim().length > 0) && (this.prop2 && this.prop2.trim().length > 0)) {
      this.dialogRef.close({prop1: this.prop1, prop2: this.prop2})
    }
  }

  langSelect(event) {
    this.prop1 = event.option.value.code;
    this.prop2 = event.option.value.name;
  }

  displayFn(language: Language): string {
    return language && language.code ? language.code : '';
  }
}
