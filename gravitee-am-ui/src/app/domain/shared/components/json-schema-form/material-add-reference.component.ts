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
import { Component, DoCheck, Input, OnInit } from '@angular/core';
import { JsonSchemaFormService } from "angular2-json-schema-form";


@Component({
  selector: 'material-add-reference-widget',
  template: `
    <section [class]="options?.htmlClass" align="end">
      <button md-raised-button *ngIf="showAddButton" style="margin-top: 10px;"
        [color]="options?.color || 'accent'"
        [disabled]="options?.readonly"
        (click)="addItem($event)">
        <span *ngIf="options?.icon" [class]="options?.icon"></span>
        <span *ngIf="options?.title" [innerHTML]="setTitle()"></span>
      </button>
    </section>`,
})
export class MaterialAddReferenceComponent implements OnInit, DoCheck {
  options: any;
  itemCount: number;
  showAddButton: boolean = true;
  previousLayoutIndex: number[];
  previousDataIndex: number[];
  @Input() formID: number;
  @Input() layoutNode: any;
  @Input() layoutIndex: number[];
  @Input() dataIndex: number[];

  constructor(
    private jsf: JsonSchemaFormService
  ) { }

  ngOnInit() {
    this.options = this.layoutNode.options || {};
    this.updateControl();
  }

  ngDoCheck() {
    if (this.previousLayoutIndex !== this.layoutIndex ||
      this.previousDataIndex !== this.dataIndex
    ) {
      this.updateControl();
    }
  }

  addItem(event) {
    event.preventDefault();
    this.itemCount = this.layoutIndex[this.layoutIndex.length - 1] + 1;
    this.jsf.addItem(this);
    this.updateControl();
  }

  updateControl() {
    this.itemCount = this.layoutIndex[this.layoutIndex.length - 1];
    this.previousLayoutIndex = this.layoutIndex;
    this.previousDataIndex = this.dataIndex;
    this.showAddButton = this.layoutNode.arrayItem &&
      this.itemCount < (this.options.maxItems || 1000000);
  }

  setTitle(): string {
    const parent: any = {
      dataIndex: this.dataIndex.slice(0, -1),
      layoutNode: this.jsf.getParentNode(this)
    };
    return this.jsf.setTitle(parent, this.layoutNode, this.itemCount);
  }
}
