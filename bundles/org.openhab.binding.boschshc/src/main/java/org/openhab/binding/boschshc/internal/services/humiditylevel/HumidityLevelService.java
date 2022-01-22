/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.boschshc.internal.services.humiditylevel;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.boschshc.internal.services.BoschSHCService;
import org.openhab.binding.boschshc.internal.services.humiditylevel.dto.HumidityLevelServiceState;

/**
 * Measures the humidity at a central point in the room.
 * 
 * @author Christian Oeing - Initial contribution
 */
@NonNullByDefault
public class HumidityLevelService extends BoschSHCService<HumidityLevelServiceState> {

    public HumidityLevelService() {
        super("HumidityLevel", HumidityLevelServiceState.class);
    }
}
