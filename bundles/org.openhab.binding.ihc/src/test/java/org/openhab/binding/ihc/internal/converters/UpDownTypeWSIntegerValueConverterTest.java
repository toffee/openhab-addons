/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.ihc.internal.converters;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.openhab.binding.ihc.internal.ws.exeptions.ConversionException;
import org.openhab.binding.ihc.internal.ws.resourcevalues.WSIntegerValue;
import org.openhab.binding.ihc.internal.ws.resourcevalues.WSResourceValue;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.types.Type;

/**
 * Test for IHC / ELKO binding
 *
 * @author Pauli Anttila - Initial contribution
 */
public class UpDownTypeWSIntegerValueConverterTest {

    @Test
    public void testOpen() throws ConversionException {
        final boolean inverted = false;
        WSIntegerValue val = new WSIntegerValue(12345, 0, -100, 100);

        val = convertFromOHType(val, UpDownType.UP, new ConverterAdditionalInfo(null, inverted, null));
        assertEquals(12345, val.resourceID);
        assertEquals(-100, val.minimumValue);
        assertEquals(100, val.maximumValue);
        assertEquals(100, val.value);

        UpDownType type = convertFromResourceValue(val, new ConverterAdditionalInfo(null, inverted, null));
        assertEquals(UpDownType.UP, type);
    }

    @Test
    public void testClosed() throws ConversionException {
        final boolean inverted = false;

        WSIntegerValue val = new WSIntegerValue(12345, 0, -100, 100);
        val = convertFromOHType(val, UpDownType.DOWN, new ConverterAdditionalInfo(null, inverted, null));
        assertEquals(12345, val.resourceID);
        assertEquals(-100, val.minimumValue);
        assertEquals(100, val.maximumValue);
        assertEquals(-100, val.value);

        UpDownType type = convertFromResourceValue(val, new ConverterAdditionalInfo(null, inverted, null));
        assertEquals(UpDownType.DOWN, type);
    }

    @Test
    public void testOpenInverted() throws ConversionException {
        final boolean inverted = true;

        WSIntegerValue val = new WSIntegerValue(12345, 0, -100, 100);
        val = convertFromOHType(val, UpDownType.UP, new ConverterAdditionalInfo(null, inverted, null));
        assertEquals(12345, val.resourceID);
        assertEquals(-100, val.minimumValue);
        assertEquals(100, val.maximumValue);
        assertEquals(-100, val.value);

        UpDownType type = convertFromResourceValue(val, new ConverterAdditionalInfo(null, inverted, null));
        assertEquals(UpDownType.UP, type);
    }

    @Test
    public void testClosedInverted() throws ConversionException {
        final boolean inverted = true;

        WSIntegerValue val = new WSIntegerValue(12345, 0, -100, 100);
        val = convertFromOHType(val, UpDownType.DOWN, new ConverterAdditionalInfo(null, inverted, null));
        assertEquals(12345, val.resourceID);
        assertEquals(-100, val.minimumValue);
        assertEquals(100, val.maximumValue);
        assertEquals(100, val.value);

        UpDownType type = convertFromResourceValue(val, new ConverterAdditionalInfo(null, inverted, null));
        assertEquals(UpDownType.DOWN, type);
    }

    private WSIntegerValue convertFromOHType(WSIntegerValue IHCvalue, Type OHval,
            ConverterAdditionalInfo converterAdditionalInfo) throws ConversionException {
        Converter<WSResourceValue, Type> converter = ConverterFactory.getInstance().getConverter(IHCvalue.getClass(),
                UpDownType.class);
        return (WSIntegerValue) converter.convertFromOHType(OHval, IHCvalue, converterAdditionalInfo);
    }

    private UpDownType convertFromResourceValue(WSIntegerValue IHCvalue,
            ConverterAdditionalInfo converterAdditionalInfo) throws ConversionException {
        Converter<WSResourceValue, Type> converter = ConverterFactory.getInstance().getConverter(IHCvalue.getClass(),
                UpDownType.class);
        return (UpDownType) converter.convertFromResourceValue(IHCvalue, converterAdditionalInfo);
    }
}
