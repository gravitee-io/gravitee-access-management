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
package io.gravitee.am.repository.mongodb.common;

import io.gravitee.am.repository.management.api.search.FilterCriteria;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FilterCriteriaParserTest {

    @Test
    public void shouldParse_eq_criteria() {
        FilterCriteria filterCriteria = new FilterCriteria();
        filterCriteria.setOperator("eq");
        filterCriteria.setFilterName("username");
        filterCriteria.setFilterValue("Alice");
        filterCriteria.setQuoteFilterValue(true);

        String query = FilterCriteriaParser.parse(filterCriteria);
        Assert.assertEquals("{\"username\":{$eq:\"Alice\"}}", query);
    }

    @Test
    public void shouldParse_exists_criteria() {
        FilterCriteria filterCriteria = new FilterCriteria();
        filterCriteria.setOperator("pr");
        filterCriteria.setFilterName("username");

        String query = FilterCriteriaParser.parse(filterCriteria);
        Assert.assertEquals("{\"username\":{$exists:true}}", query);
    }

    @Test
    public void shouldParse_nested_criteria() {
        FilterCriteria filterCriteria = new FilterCriteria();
        filterCriteria.setOperator("eq");
        filterCriteria.setFilterName("name.username");
        filterCriteria.setFilterValue("Alice");
        filterCriteria.setQuoteFilterValue(true);

        String query = FilterCriteriaParser.parse(filterCriteria);
        Assert.assertEquals("{\"name.username\":{$eq:\"Alice\"}}", query);
    }

    @Test
    public void shouldParse_compose_criteria() {
        FilterCriteria filterCriteria = new FilterCriteria();
        filterCriteria.setOperator("and");

        FilterCriteria leftPart = new FilterCriteria();
        leftPart.setOperator("eq");
        leftPart.setFilterName("username");
        leftPart.setFilterValue("Alice");
        leftPart.setQuoteFilterValue(true);

        FilterCriteria rightPart = new FilterCriteria();
        rightPart.setOperator("or");

        FilterCriteria rightLeftPart = new FilterCriteria();
        rightLeftPart.setOperator("co");
        rightLeftPart.setFilterName("email");
        rightLeftPart.setFilterValue("Alice");
        rightLeftPart.setQuoteFilterValue(true);

        FilterCriteria rightRightPart = new FilterCriteria();
        rightRightPart.setOperator("sw");
        rightRightPart.setFilterName("nickname");
        rightRightPart.setFilterValue("Alice");
        rightRightPart.setQuoteFilterValue(true);

        rightPart.setFilterComponents(Arrays.asList(rightLeftPart, rightRightPart));
        filterCriteria.setFilterComponents(Arrays.asList(leftPart, rightPart));

        String query = FilterCriteriaParser.parse(filterCriteria);
        Assert.assertEquals("{$and:[{\"username\":{$eq:\"Alice\"}},{$or:[{\"email\":{$regex:\"Alice\",$options:\"i\"}},{\"nickname\":{$regex:\"^Alice\",$options:\"i\"}}]}]}", query);
    }
}
