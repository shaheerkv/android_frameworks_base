/*
* Copyright (C) 2014 SlimRoms Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.android.internal.util.liquid;

import android.os.SystemProperties;

public class BuildInfo {

    private static final String BUILD_TYPE_PROP = "ro.liquid.buildtype";
    private static final String LIQUID_VERSION_PROP = "ro.liquid.version";

    public static String getLiquidBuildType() {
        return SystemProperties.get(BUILD_TYPE_PROP, "");
    }

    public static String getLiquidVersion() {
        return SystemProperties.get(LIQUID_VERSION_PROP, "");
    }
}
