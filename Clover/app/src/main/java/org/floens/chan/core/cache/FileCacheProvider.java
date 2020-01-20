/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.core.cache;

import android.content.Context;
import android.net.Uri;
import androidx.core.content.FileProvider;

import org.floens.chan.utils.AndroidUtils;

import java.io.File;

public class FileCacheProvider {
    public static Uri getUriForFile(File file) {
        Context applicationContext = AndroidUtils.getAppContext();
        String authority = getAuthority(applicationContext);
        return FileProvider.getUriForFile(applicationContext, authority, file);
    }

    private static String getAuthority(Context applicationContext) {
        // NOTE: keep this in line with the name defined in the different manifests for the
        // different flavors.
        return applicationContext.getPackageName() + ".fileprovider";
    }
}
