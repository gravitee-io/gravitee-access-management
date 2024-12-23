/**
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
package io.gravitee.am.service.validators.plugincfg;

import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidator.Result;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidatorsRegistry;
import io.gravitee.am.service.model.PluginConfigurationPayload;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PluginJsonFormValidator implements ConstraintValidator<PluginJsonForm, PluginConfigurationPayload> {
    private final PluginConfigurationValidatorsRegistry pluginValidatorsRegistry;

    @Override
    public boolean isValid(PluginConfigurationPayload newPluginInstance, ConstraintValidatorContext ctx) {
        return pluginValidatorsRegistry.get(newPluginInstance.getType())
                .map(validator -> validator.validate(newPluginInstance.getConfiguration()))
                .map(result -> processResult(result, ctx))
                .orElse(Boolean.TRUE);
    }

    private boolean processResult(Result result, ConstraintValidatorContext ctx){
        if(result.isValid()){
            return true;
        } else {
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(result.getMsg()).addConstraintViolation();
            return false;
        }
    }

}
