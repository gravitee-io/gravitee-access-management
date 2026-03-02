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
package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.model.UserBindingCriterion;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * MongoDB representation of UserBindingCriterion (trusted issuer user binding).
 */
@Getter
@Setter
public class UserBindingCriterionMongo {

    private String attribute;
    private String expression;

    public UserBindingCriterion convert() {
        UserBindingCriterion c = new UserBindingCriterion();
        c.setAttribute(getAttribute());
        c.setExpression(getExpression());
        return c;
    }

    public static UserBindingCriterionMongo convert(UserBindingCriterion c) {
        if (c == null) {
            return null;
        }
        UserBindingCriterionMongo m = new UserBindingCriterionMongo();
        m.setAttribute(c.getAttribute());
        m.setExpression(c.getExpression());
        return m;
    }

    public static List<UserBindingCriterion> toModelList(List<UserBindingCriterionMongo> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(UserBindingCriterionMongo::convert).toList();
    }

    public static List<UserBindingCriterionMongo> fromModelList(List<UserBindingCriterion> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(UserBindingCriterionMongo::convert).toList();
    }
}
