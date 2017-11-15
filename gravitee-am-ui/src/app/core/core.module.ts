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
import {NgModule, Optional, SkipSelf} from "@angular/core";
import {CommonModule} from "@angular/common";
import {RouterModule} from "@angular/router";
import {Http, HttpModule} from "@angular/http";
import {FlexLayoutModule} from "@angular/flex-layout";
import {
  MdButtonModule, MdDialogModule, MdIconModule, MdListModule, MdMenuModule, MdSidenavModule, MdToolbarModule,
  MdTooltipModule
} from "@angular/material";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {AppConfig} from "../../config/app.config";
import {NavbarComponent} from "./components/navbar/navbar.component";
import {SidenavComponent} from "./components/sidenav/sidenav.component";
import {SidenavService} from "./components/sidenav/sidenav.service";
import {AuthService} from "./services/auth.service";
import {DialogService} from "./services/dialog.service";
import {SnackbarService} from "./services/snackbar.service";
import {HttpService} from "./services/http.service";
import {ConfirmComponent} from "./components/dialog/confirm/confirm.component";

@NgModule({
  imports: [
    CommonModule,
    RouterModule,
    FormsModule,
    ReactiveFormsModule,
    FlexLayoutModule,
    HttpModule,
    MdMenuModule,
    MdTooltipModule,
    MdSidenavModule,
    MdIconModule,
    MdListModule,
    MdButtonModule,
    MdToolbarModule,
    MdDialogModule
  ],
  declarations: [
    NavbarComponent,
    SidenavComponent,
    ConfirmComponent
  ],
  providers: [
    SidenavService,
    AppConfig,
    AuthService,
    DialogService,
    SnackbarService,
    { provide: Http, useClass: HttpService }
  ],
  entryComponents: [
    ConfirmComponent
  ],
  exports: [
    CommonModule,
    RouterModule,
    FormsModule,
    ReactiveFormsModule,
    MdMenuModule,
    MdTooltipModule,
    MdSidenavModule,
    NavbarComponent,
    SidenavComponent
  ]
})
export class CoreModule {
  constructor (@Optional() @SkipSelf() parentModule: CoreModule) {
    if (parentModule) {
      throw new Error(
        'CoreModule is already loaded. Import it in the AppModule only');
    }
  }
}
