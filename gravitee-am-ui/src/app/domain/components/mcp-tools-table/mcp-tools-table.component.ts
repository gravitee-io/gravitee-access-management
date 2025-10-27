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
import { Component, EventEmitter, Input, Output, ViewEncapsulation } from '@angular/core';

export interface McpTool {
  key: string;
  description: string;
  scopes: string[];
}

@Component({
  selector: 'mcp-tools-table',
  templateUrl: './mcp-tools-table.component.html',
  styleUrls: ['./mcp-tools-table.component.scss'],
  encapsulation: ViewEncapsulation.None,
  standalone: false,
})
export class McpToolsTableComponent {
  @Input() tools: McpTool[] = [];
  @Input() showEditIcon = false;
  @Input() showDeleteIcon = false;

  @Output() edit = new EventEmitter<McpTool>();
  @Output() delete = new EventEmitter<McpTool>();

  handleEdit(tool: McpTool): void {
    this.edit.emit(tool);
  }

  handleDelete(tool: McpTool): void {
    this.delete.emit(tool);
  }
}
