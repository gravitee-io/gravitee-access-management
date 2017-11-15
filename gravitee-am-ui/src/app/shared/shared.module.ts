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
import {NgModule} from "@angular/core";
import {CommonModule} from "@angular/common";
import {RouterModule} from "@angular/router";
import {FlexLayoutModule} from "@angular/flex-layout";
import {
  MdButtonModule, MdIconModule, MdInputModule, MdListModule, MdProgressSpinnerModule,
  MdSnackBarModule
} from "@angular/material";
import {Ng2BreadcrumbModule} from "../../libraries/ng2-breadcrumb/app.module";
import {EmptystateComponent} from "./components/emptystate/emptystate.component";
import {BreadcrumbComponent} from "./components/breadcrumb/breadcrumb.component";
import {HumanDatePipe} from "./pipes/human-date.pipe";
import {WidgetTotalClientsComponent} from "./components/widget/total-clients/total-clients.component";
import {WidgetTotalTokensComponent} from "./components/widget/total-tokens/total-tokens.component";
import {WidgetClientsComponent} from "./components/widget/clients/clients.component";
import {WidgetTopClientsComponent} from "./components/widget/top-clients/top-clients.component";
import {MapToIterablePipe} from "./pipes/map-to-iterable.pipe";

@NgModule({
  imports: [
    CommonModule,
    RouterModule,
    FlexLayoutModule,
    MdIconModule,
    MdListModule,
    MdInputModule,
    MdButtonModule,
    MdSnackBarModule,
    MdProgressSpinnerModule,
    Ng2BreadcrumbModule.forRoot()
  ],
  declarations: [
    EmptystateComponent,
    BreadcrumbComponent,
    HumanDatePipe,
    MapToIterablePipe,
    WidgetTotalClientsComponent,
    WidgetTotalTokensComponent,
    WidgetTopClientsComponent,
    WidgetClientsComponent,
  ],
  exports: [
    FlexLayoutModule,
    MdIconModule,
    MdListModule,
    EmptystateComponent,
    BreadcrumbComponent,
    HumanDatePipe,
    MapToIterablePipe,
    WidgetTotalClientsComponent,
    WidgetTotalTokensComponent,
    WidgetTopClientsComponent,
    WidgetClientsComponent
  ]
})
export class SharedModule { }
