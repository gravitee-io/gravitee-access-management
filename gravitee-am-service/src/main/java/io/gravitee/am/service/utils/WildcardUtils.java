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
package io.gravitee.am.service.utils;

import java.util.regex.Pattern;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface WildcardUtils {

    char ASTERISK = '*';
    String PATTERN = ".*";

    static String toRegex(String pattern) {
        if (pattern == null) {
            return null;
        }
        var patternBuilder = new StringBuilder();
        final char[] patternArray = pattern.toCharArray();
        int currentIndex = 0;
        for (int i = 0; i < patternArray.length; i++) {
            char c = patternArray[i];
            if (c == ASTERISK) {
                protectPattern(pattern, patternBuilder, currentIndex, i);
                patternBuilder.append(PATTERN);
                currentIndex = i + 1;
            }
        }

        // we need to protect the end of the pattern
        final int lastIndex = patternArray.length - 1;
        if (currentIndex < lastIndex && patternArray[lastIndex] != ASTERISK) {
            protectPattern(pattern, patternBuilder, currentIndex, patternArray.length);
        }

        return patternBuilder.toString();
    }

    private static void protectPattern(String pattern, StringBuilder patternBuilder, int firstIndex, int i) {
        if (firstIndex != i) {
            patternBuilder.append(Pattern.quote(pattern.substring(firstIndex, i)));
        }
    }
}
