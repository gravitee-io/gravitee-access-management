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
package io.gravitee.am.repository.jdbc.provider.utils;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class ObjectUtils {

    public static Object stringToValue(String string) {
        if (string.equals("")) {
            return string;
        } else if (string.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        } else if (string.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        } else if (string.equalsIgnoreCase("null")) {
            return null;
        } else {
            char initial = string.charAt(0);
            if (initial >= '0' && initial <= '9' || initial == '-') {
                try {
                    if (isDecimalNotation(string)) {
                        Double d = Double.valueOf(string);
                        if (!d.isInfinite() && !d.isNaN()) {
                            return d;
                        }
                    } else {
                        Long myLong = Long.valueOf(string);
                        if (string.equals(myLong.toString())) {
                            if (myLong == (long)myLong.intValue()) {
                                return myLong.intValue();
                            }

                            return myLong;
                        }
                    }
                } catch (Exception var3) {
                }
            }

            return string;
        }
    }

    private static boolean isDecimalNotation(String val) {
        return val.indexOf(46) > -1 || val.indexOf(101) > -1 || val.indexOf(69) > -1 || "-0".equals(val);
    }
}
