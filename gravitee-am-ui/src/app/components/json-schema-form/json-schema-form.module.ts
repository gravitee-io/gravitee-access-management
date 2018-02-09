import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import {
  Framework,
  FrameworkLibraryService,
  JsonSchemaFormService, MaterialDesignFramework, MaterialDesignFrameworkModule,
  WidgetLibraryModule, WidgetLibraryService
} from 'angular2-json-schema-form';
import {JsonSchemaFormComponent} from "./json-schema-form.component";

@NgModule({
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule,
    WidgetLibraryModule, MaterialDesignFrameworkModule
  ],
  declarations: [
    JsonSchemaFormComponent
  ],
  providers: [
    JsonSchemaFormService,
    FrameworkLibraryService,
    WidgetLibraryService,
    {
      provide: Framework,
      useClass: MaterialDesignFramework,
      multi: true
    }
  ],
  exports: [ JsonSchemaFormComponent, WidgetLibraryModule ]
})

export class JsonSchemaFormModule {}
