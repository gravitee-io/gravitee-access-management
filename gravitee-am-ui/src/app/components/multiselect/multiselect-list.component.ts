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
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { MatSelectChange } from '@angular/material/select';

@Component({
  selector: 'gv-multiselect-list',
  templateUrl: './multiselect-list.component.html',
  styleUrls: ['./multiselect-list.component.scss'],
  standalone: false,
})
export class MultiselectListComponent implements OnChanges {
  @Input() label: string;
  @Input() hint: string;
  @Input() emptySelectionMessage: string;
  @Input() testIdPrefix: string;
  @Input() items: string[] = [];
  @Input() selected: string[] = [];
  @Output() selectedChange = new EventEmitter<string[]>();

  searchText = '';
  visibleItems: string[] = [];
  selectAllLabel = 'Select all';
  selectAllState: 'checked' | 'unchecked' | 'indeterminate' = 'unchecked';

  ngOnChanges(changes: SimpleChanges) {
    if (changes.selected) {
      this.selected = this.selected || [];
    }
    if (changes.items) {
      this.items = this.items || [];
    }
    this.refreshOptions();
  }

  testId(suffix: string): string {
    return this.testIdPrefix ? this.testIdPrefix + suffix : null;
  }

  onSearch(event: Event) {
    this.searchText = (event.target as HTMLInputElement).value;
    this.refreshOptions();
  }

  onUnselect(item: string) {
    this.select(this.selected.filter((i) => i !== item));
  }

  onSelectionChange(event: MatSelectChange<string[]>) {
    this.select(event.value);
  }

  toggleSelectVisible() {
    if (this.visibleItems.length === 0) return;

    const selectedVisible = this.visibleItems.filter((i) => this.selected.includes(i));

    const allVisibleSelected = selectedVisible.length === this.visibleItems.length;

    if (allVisibleSelected) {
      this.select(this.selected.filter((i) => !this.visibleItems.includes(i)));
    } else {
      const merged = new Set([...this.selected, ...this.visibleItems]);
      this.select(Array.from(merged));
    }
  }

  private select(selected: string[]) {
    this.selected = selected || [];
    this.refreshOptions();
    this.selectedChange.emit(this.selected);
  }

  private refreshOptions() {
    const query = this.searchText.toLowerCase();

    this.visibleItems = (this.items || []).filter((item) => item.toLowerCase().includes(query));

    this.selectAllLabel = query ? 'Select visible' : 'Select all';

    if (this.visibleItems.length === 0) {
      this.selectAllState = 'unchecked';
      return;
    }

    const selectedVisible = this.visibleItems.filter((i) => this.selected.includes(i));

    if (selectedVisible.length === 0) {
      this.selectAllState = 'unchecked';
    } else if (selectedVisible.length === this.visibleItems.length) {
      this.selectAllState = 'checked';
    } else {
      this.selectAllState = 'indeterminate';
    }
  }
}
