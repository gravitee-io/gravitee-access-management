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
import {AfterViewInit, Component, EventEmitter, Input, OnInit, Output, ViewChild} from '@angular/core';
import {MatPaginator, MatSort, MatTableDataSource} from '@angular/material';
import {SelectionModel} from '@angular/cdk/collections';
import {ActivatedRoute} from '@angular/router';
import * as _ from 'lodash';

export interface Scope {
  id: string;
  key: string;
  name: string;
  description: string;
  discovery: boolean;
}

@Component({
  selector: 'scope-selection',
  templateUrl: './scope-selection.component.html',
  styleUrls: ['./scope-selection.component.scss']
})

export class ScopeSelectionComponent implements OnInit, AfterViewInit {
  @Output() onScopeSelection = new EventEmitter();
  @Input() initialSelectedScopes: string[];
  @Input() readonly: boolean;

  scopes: MatTableDataSource<Scope>;
  selection: SelectionModel<Scope>;
  displayedColumns: string[] = ['select', 'key', 'name', 'description'];

  @ViewChild(MatSort, { static: true }) sort: MatSort;
  @ViewChild(MatPaginator, { static: true }) paginator: MatPaginator;

  constructor(private route: ActivatedRoute) {}

  ngOnInit() {
    const datasource = _.map(this.route.snapshot.data['scopes'],  scope => <Scope>{
      id: scope.id, key: scope.key, name: scope.name, description: scope.description, discovery: scope.discovery
    });
    this.scopes = new MatTableDataSource(datasource);
    this.scopes.paginator = this.paginator;
    const initialSelectedValues = _.intersectionWith(this.scopes.data, this.initialSelectedScopes, (scope, key) => scope.key === key);
    this.selection = new SelectionModel<Scope>(true, _.compact(initialSelectedValues));
  }

  ngAfterViewInit() {
    // implement custom sort to manage select checkbox
    this.scopes.sortingDataAccessor = (scope, property) => {
      switch (property) {
        case 'select': {
          return this.selection.isSelected(scope) ? 0 : 1;
        }
        default: {
          return scope[property];
        }
      }
    };
    this.applySort();
  }

  applyChange(row) {
    this.selection.toggle(row);
    this.applySort();
    this.onScopeSelection.emit(this.selection.selected);
  }

  /** Apply sort on table */
  applySort() {
    this.scopes.sort = this.sort;
  }

  /** Apply filter on table */
  applyFilter(filterValue: string) {
    this.scopes.filter = filterValue.trim().toLowerCase();
  }

  /** Whether the number of selected elements matches the total number of rows. */
  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.scopes.data.length;
    return numSelected === numRows;
  }

  /** Selects all rows if they are not all selected; otherwise clear selection. */
  masterToggle() {
    this.isAllSelected() ?
      this.selection.clear() :
      this.scopes.data.forEach(row => this.selection.select(row));
    this.onScopeSelection.emit(this.selection.selected);
  }

  /** The label for the checkbox on the passed row */
  checkboxLabel(row?: Scope): string {
    if (!row) {
      return `${this.isAllSelected() ? 'select' : 'deselect'} all`;
    }
    return `${this.selection.isSelected(row) ? 'deselect' : 'select'} row ${row.id + 1}`;
  }
}
