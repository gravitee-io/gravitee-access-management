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
import { Component, Input, OnInit } from '@angular/core';
import { JsonSchemaFormService } from '@ajsf/core';
import { HttpClient } from '@angular/common/http';
import { take } from 'rxjs/operators';

import { AppConfig } from '../../../config/app.config';

@Component({
  selector: 'material-multiselect-widget',
  template: `
    <gv-multiselect-list
      [label]="options.title"
      [hint]="options.description"
      [emptySelectionMessage]="options.onEmptySelectionMessage"
      [items]="allItems"
      [selected]="selected"
      (selectedChange)="onSelectedChange($event)"
    >
    </gv-multiselect-list>
  `,
  standalone: false,
})
export class MaterialMultiselectComponent implements OnInit {
  @Input() layoutNode: any;
  @Input() controlName: string;

  options: any;
  allItems: string[] = [];
  selected: string[] = [];

  private baseURL = AppConfig.settings.baseURL;

  constructor(
    private jsf: JsonSchemaFormService,
    private http: HttpClient,
  ) {}

  ngOnInit() {
    this.options = this.layoutNode.options;
    this.jsf.initializeControl(this);
    this.onSelectedChange(this.jsf.data[this.controlName] || []);

    if (this.options.itemsDictionaryEndpoint) {
      const url = this.baseURL + this.options.itemsDictionaryEndpoint;
      this.http
        .get<any>(url)
        .pipe(take(1))
        .subscribe((data) => {
          this.allItems = data;
        });
    } else {
      this.allItems = this.options.enum || [];
    }
  }

  onSelectedChange(selected: string[]) {
    this.selected = selected || [];
    const updatedValues = this.selected.map((t: string) => ({
      name: t,
      value: t,
      checked: true,
    }));

    this.jsf.updateArrayCheckboxList(this, updatedValues);
  }
}
