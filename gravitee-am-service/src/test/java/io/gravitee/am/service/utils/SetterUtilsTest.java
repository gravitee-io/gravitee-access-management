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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Optional;

import static org.junit.Assert.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(JUnit4.class)
public class SetterUtilsTest {

    @Test
    public void testSafeSet_optionalIsNull() {
        BasicDto basic = new BasicDto();
        SetterUtils.safeSet(basic::setString,null);
        assertNull("was expecting a null value",basic.getString());
    }

    @Test
    public void testSafeSet_optionalIsEmpty() {
        BasicDto basic = new BasicDto();
        basic.setString("oldValue");
        SetterUtils.safeSet(basic::setString,Optional.empty());
        assertNull(basic.getString());
    }

    @Test
    public void testSafeSet_optionalIsNotNull() {
        BasicDto basic = new BasicDto();
        SetterUtils.safeSet(basic::setString,Optional.of("succeed"));
        assertNotNull(basic.getString());
        assertEquals("succeed",basic.getString());
    }

    @Test
    public void testSafeSet_emptyPrimitive() {

        BasicDto basic = new BasicDto();
        basic.setString("basic");
        basic.setBoolean(true);
        basic.setByte(Byte.MAX_VALUE);
        basic.setChar(Character.MAX_VALUE);
        basic.setShort(Short.MAX_VALUE);
        basic.setInt(Integer.MAX_VALUE);
        basic.setLong(Long.MAX_VALUE);
        basic.setfloat(Float.MAX_VALUE);
        basic.setDouble(Double.MAX_VALUE);

        SetterUtils.safeSet(basic::setString,Optional.empty(),String.class);
        SetterUtils.safeSet(basic::setBoolean,Optional.empty(),boolean.class);
        SetterUtils.safeSet(basic::setByte,Optional.empty(),byte.class);
        SetterUtils.safeSet(basic::setChar,Optional.empty(),char.class);
        SetterUtils.safeSet(basic::setShort,Optional.empty(),short.class);
        SetterUtils.safeSet(basic::setInt,Optional.empty(),int.class);
        SetterUtils.safeSet(basic::setLong,Optional.empty(),long.class);
        SetterUtils.safeSet(basic::setfloat,Optional.empty(),float.class);
        SetterUtils.safeSet(basic::setDouble,Optional.empty(),double.class);

        BasicDto defaultValue = new BasicDto();
        assertEquals("non primitive should be set to null",defaultValue.getString(),basic.getString());
        assertEquals("boolean non default value",defaultValue.getBoolean(),basic.getBoolean());
        assertEquals("byte non default value",defaultValue.getByte(),basic.getByte());
        assertEquals("char non default value",defaultValue.getChar(),basic.getChar());
        assertEquals("short non default value",defaultValue.getShort(),basic.getShort());
        assertEquals("int non default value",defaultValue.getInt(),basic.getInt());
        assertEquals("long non default value",defaultValue.getLong(),basic.getLong());
        assertEquals("float non default value",defaultValue.getFloat(),basic.getFloat(),1e-15);
        assertEquals("double non default value",defaultValue.getDouble(),basic.getDouble(),1e-15);
    }

    @Test
    public void testSafeSet_nonEmptyPrimitive() {

        BasicDto basic = new BasicDto();

        SetterUtils.safeSet(basic::setString,Optional.of("succeed"),String.class);
        SetterUtils.safeSet(basic::setBoolean,Optional.of(true),boolean.class);
        SetterUtils.safeSet(basic::setByte,Optional.of(Byte.MAX_VALUE),byte.class);
        SetterUtils.safeSet(basic::setChar,Optional.of(Character.MAX_VALUE),char.class);
        SetterUtils.safeSet(basic::setShort,Optional.of(Short.MAX_VALUE),short.class);
        SetterUtils.safeSet(basic::setInt,Optional.of(Integer.MAX_VALUE),int.class);
        SetterUtils.safeSet(basic::setLong,Optional.of(Long.MAX_VALUE),long.class);
        SetterUtils.safeSet(basic::setfloat,Optional.of(Float.MAX_VALUE),float.class);
        SetterUtils.safeSet(basic::setDouble,Optional.of(Double.MAX_VALUE),double.class);

        assertEquals("non primitive should be set to null","succeed",basic.getString());
        assertEquals("boolean non default value",true,basic.getBoolean());
        assertEquals("byte non default value",Byte.MAX_VALUE,basic.getByte());
        assertEquals("char non default value",Character.MAX_VALUE,basic.getChar());
        assertEquals("short non default value",Short.MAX_VALUE,basic.getShort());
        assertEquals("int non default value",Integer.MAX_VALUE,basic.getInt());
        assertEquals("long non default value",Long.MAX_VALUE,basic.getLong());
        assertEquals("float non default value",Float.MAX_VALUE,basic.getFloat(),1e-15);
        assertEquals("double non default value",Double.MAX_VALUE,basic.getDouble(),1e-15);
    }

    @Test
    public void testSafeSetOrElse_optionalIsNull() {
        BasicDto basic = new BasicDto();
        SetterUtils.safeSetOrElse(basic::setString,null,"orElse");
        assertNull("was expecting a null value",basic.getString());
    }

    @Test
    public void testSafeSetOrElse_optionalIsEmpty() {
        BasicDto basic = new BasicDto();
        SetterUtils.safeSetOrElse(basic::setString,Optional.empty(),"orElse");
        assertNotNull("was expecting a non null value",basic.getString());
        assertEquals("orElse",basic.getString());
    }

    @Test
    public void testSafeSetOrElse_optionalIsNotEmpty() {
        BasicDto basic = new BasicDto();
        SetterUtils.safeSetOrElse(basic::setString,Optional.of("succeed"),"wasEmpty");
        assertNotNull(basic.getString());
        assertEquals("succeed",basic.getString());
    }

    private class BasicDto {
        private String objet;
        private boolean solo;
        private byte code;
        private char dassaut;
        private short y;
        private int elligent;
        private long island;
        private float aison;
        private double jeu;

        public String getString() { return objet; }
        public void setString(String objet) { this.objet = objet; }
        public boolean getBoolean() { return solo; }
        public void setBoolean(boolean solo) { this.solo = solo; }
        public byte getByte() { return code; }
        public void setByte(byte code) { this.code = code; }
        public char getChar() { return dassaut; }
        public void setChar(char dassaut) { this.dassaut = dassaut; }
        public short getShort() { return y; }
        public void setShort(short y) { this.y = y; }
        public int getInt() { return elligent; }
        public void setInt(int elligent) { this.elligent = elligent; }
        public long getLong() { return island; }
        public void setLong(long island) { this.island = island; }
        public float getFloat() { return aison; }
        public void setfloat(float aison) { this.aison = aison; }
        public double getDouble() { return jeu; }
        public void setDouble(double jeu) { this.jeu = jeu; }
    }
}
