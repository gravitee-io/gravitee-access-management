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
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import 'hammerjs';
import 'codemirror';
import "codemirror/mode/htmlmixed/htmlmixed";
import "codemirror/addon/selection/mark-selection";

import { CoreModule } from "./core/core.module";
import { DashboardModule } from "./dashboard/dashboard.module";
import { ClientsModule } from "./clients/clients.module";
import { DomainModule } from "./domain/domain.module";
import { SettingsModule } from "./settings/settings.module";
import { LoginModule } from "./login/login.module";
import { LogoutModule } from "./logout/logout.module";
import { OAuthModule } from "./oauth/oauth.module";
import { DummyModule } from "./dummy/dummy.module";

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';

@NgModule({
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    CoreModule,
    DashboardModule,
    ClientsModule,
    DomainModule,
    SettingsModule,
    LoginModule,
    LogoutModule,
    OAuthModule,
    DummyModule,
    AppRoutingModule,
  ],
  declarations: [
    AppComponent
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
