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
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JSONSchema7 } from 'json-schema';

import { AppConfig } from '../../config/app.config';

export interface DataSource {
  id: string;
  name: string;
}

// Extend JSONSchema7 to include custom properties used by the application
export interface ExtendedJSONSchema extends JSONSchema7 {
  widget?: string;
  'x-schema-form'?: any;
  readonly?: boolean;
}

@Injectable()
export class DataSourcesService {
  private baseURL: string = AppConfig.settings.environmentBaseURL;

  constructor(private http: HttpClient) {}

  findAll(): Observable<DataSource[]> {
    return this.http.get<DataSource[]>(this.baseURL + '/data-sources');
  }

  /**
   * Applies datasource selection transformations to a schema
   * @param schema The schema to process
   * @param datasources Array of available datasources
   * @returns The processed schema with datasource widgets transformed
   */
  applyDataSourceSelection(schema: ExtendedJSONSchema, datasources: DataSource[]): ExtendedJSONSchema {
    if (schema && schema.properties) {
      for (const key in schema.properties) {
        const property = schema.properties[key] as ExtendedJSONSchema;
        this.applyDataSourceSelectionRecursive(property, key, datasources);
      }
    }
    return schema;
  }

  /**
   * Recursively processes schema properties to handle nested objects and arrays
   * @param property The property to process
   * @param propertyName The name of the property
   * @param datasources Array of available datasources
   */
  private applyDataSourceSelectionRecursive(property: ExtendedJSONSchema, propertyName?: string, datasources?: DataSource[]): void {
    // Handle nested objects
    if (property.type === 'object' && property.properties) {
      for (const key in property.properties) {
        const child = property.properties[key] as ExtendedJSONSchema;
        this.applyDataSourceSelectionRecursive(child, key, datasources);
      }
    }

    // Handle arrays
    if (property.type === 'array' && property.items) {
      const itemsSchema = property.items as ExtendedJSONSchema;
      if (itemsSchema.properties) {
        for (const key in itemsSchema.properties) {
          const child = itemsSchema.properties[key] as ExtendedJSONSchema;
          this.applyDataSourceSelectionRecursive(child, key, datasources);
        }
      }
    }

    // Apply datasource widget transformation
    this.applyDataSourceWidget(property, propertyName, datasources);
  }

  /**
   * Transforms datasource widgets into select dropdowns
   * @param property The property to transform
   * @param propertyName The name of the property
   * @param datasources Array of available datasources
   */
  private applyDataSourceWidget(property: ExtendedJSONSchema, propertyName?: string, datasources?: DataSource[]): void {
    if ('datasource' === property.widget || 'datasource' === propertyName) {
      if (datasources?.length > 0) {
        property['x-schema-form'] = { type: 'select' };
        property.enum = datasources.map((d) => d.id);
        property['x-schema-form'].titleMap = datasources.reduce((map, obj) => {
          map[obj.id] = obj.name;
          return map;
        }, {});
      } else {
        // if list of datasources is empty, disable the field
        property['readonly'] = true;
      }
    }
  }
}
