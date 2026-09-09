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
import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';

import { OrganizationService } from '../../../../../../services/organization.service';

import {
  AttributeMappingsChange,
  AttributeMappingsSeed,
  attributeMappingErrors,
  EXPORTED_NAME_PATTERN,
  MAX_ATTRIBUTE_MAPPINGS,
  MAX_EXPORTED_NAME_LENGTH,
  MAX_EXPRESSION_LENGTH,
  ReporterAttributeMapping,
} from './reporter-attribute-mappings.types';

@Component({
  selector: 'app-reporter-attribute-mappings',
  templateUrl: './reporter-attribute-mappings.component.html',
  styleUrls: ['./reporter-attribute-mappings.component.scss'],
  standalone: false,
})
export class ReporterAttributeMappingsComponent implements OnInit, OnChanges {
  /** Only a new object reseeds the rows, discarding local edits. */
  @Input() seed: AttributeMappingsSeed;
  @Output() attributeMappingsChanged = new EventEmitter<AttributeMappingsChange>();

  readonly exportedNamePattern = EXPORTED_NAME_PATTERN;
  readonly maxExportedNameLength = MAX_EXPORTED_NAME_LENGTH;
  readonly maxExpressionLength = MAX_EXPRESSION_LENGTH;
  readonly maxMappings = MAX_ATTRIBUTE_MAPPINGS;

  rows: ReporterAttributeMapping[] = [];
  eventTypes: string[] = [];
  allEventTypes: string[] = [];

  constructor(private organizationService: OrganizationService) {}

  ngOnInit() {
    this.organizationService.auditEventTypes().subscribe((types) => (this.allEventTypes = types || []));
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.seed) {
      // A freshly loaded page must not look changed, so seeding never emits.
      this.rows = (this.seed?.mappings ?? []).map((mapping) => ({ ...mapping }));
      this.eventTypes = [...(this.seed?.eventTypes ?? [])];
    }
  }

  get errors(): string[] {
    return attributeMappingErrors(this.rows, this.eventTypes);
  }

  get isValid(): boolean {
    return this.errors.length === 0;
  }

  get canAdd(): boolean {
    return this.rows.length < this.maxMappings;
  }

  addMapping() {
    this.rows = [...this.rows, { exportedName: '', expression: '' }];
    this.emit();
  }

  removeMapping(index: number) {
    this.rows = this.rows.filter((row, i) => i !== index);
    this.emit();
  }

  onRowInput() {
    this.emit();
  }

  onEventTypesChange(eventTypes: string[]) {
    this.eventTypes = eventTypes || [];
    this.emit();
  }

  private emit() {
    this.attributeMappingsChanged.emit({
      mappings: this.rows.map((row) => ({ ...row })),
      eventTypes: [...this.eventTypes],
      isValid: this.isValid,
    });
  }
}
