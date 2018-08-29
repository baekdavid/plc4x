/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/
package org.apache.plc4x.java.s7.model;

import org.apache.plc4x.java.api.exceptions.PlcInvalidFieldException;
import org.apache.plc4x.java.s7.netty.model.types.MemoryArea;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class S7BitField extends S7Field {

    private static final Pattern ADDRESS_PATTERN =
        Pattern.compile("^(?<memoryArea>.*?)/(?<byteOffset>\\d{1,4})/(?<bitOffset>\\d)");

    public static boolean matches(String addressString) {
        return ADDRESS_PATTERN.matcher(addressString).matches();
    }

    public static S7BitField of(String fieldString) throws PlcInvalidFieldException {
        Matcher matcher = ADDRESS_PATTERN.matcher(fieldString);
        if (!matcher.matches()) {
            throw new PlcInvalidFieldException(fieldString, ADDRESS_PATTERN);
        }
        MemoryArea memoryArea = MemoryArea.valueOf(matcher.group("memoryArea"));
        short byteOffset = Short.parseShort(matcher.group("byteOffset"));
        byte bitOffset = Byte.valueOf(matcher.group("bitOffset"));
        return new S7BitField(memoryArea, byteOffset, bitOffset);
    }

    private final byte bitOffset;

    public S7BitField(MemoryArea memoryArea, short byteOffset, byte bitOffset) {
        super(memoryArea, byteOffset);
        this.bitOffset = bitOffset;
    }

    public byte getBitOffset() {
        return bitOffset;
    }

}