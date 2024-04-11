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
import { FlexModule } from '@angular/flex-layout';
import { FormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { NgIf } from '@angular/common';
import { GioSafePipeModule } from '@gravitee/ui-particles-angular';
import { MatCheckbox } from '@angular/material/checkbox';

export interface IdpDataModel {
  id: string;
  name: string;
  association: string;
  selected: boolean;
  type: {
    icon: string;
    name: string;
  };
}

@Component({
  selector: 'app-password-policies-idp-select-table',
  standalone: true,
  imports: [FlexModule, FormsModule, MatButton, MatIcon, NgxDatatableModule, NgIf, GioSafePipeModule, MatCheckbox],
  templateUrl: './password-policies-idp-select-table.component.html',
  styleUrl: './password-policies-idp-select-table.component.scss',
})
export class PasswordPoliciesIdpSelectTableComponent implements OnInit {
  @Input() rows: IdpDataModel[];
  @Input() showAssociation: boolean;
  @Input() withSelectionWarning: boolean;

  @Output() selectionEvent = new EventEmitter<{ id: string; selected: boolean }>();

  selectionModel: Map<string, boolean>;

  ngOnInit() {
    const selection = this.rows.map((idp): [string, boolean] => [idp.id, idp.selected]);
    this.selectionModel = new Map<string, boolean>(selection);
  }

  getIcon(idp: IdpDataModel) {
    if (idp && idp.type && idp.type.icon) {
      return `<img width="24" height="24" src="${idp.type.icon}" alt="${idp.type.name} image" title="${idp.type.name}"/>`;
    }
    return `<span class="material-icons">storage</span>`;
  }

  select(rowId: string): void {
    const value = this.selectionModel.get(rowId);
    this.selectionModel.set(rowId, !value);
    this.selectionEvent.emit({ id: rowId, selected: !value });
  }
}
