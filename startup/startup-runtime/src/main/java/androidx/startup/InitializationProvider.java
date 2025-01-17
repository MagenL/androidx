/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.startup;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The {@link ContentProvider} which discovers {@link Initializer}s in an application and
 * initializes them before {@link Application#onCreate()}.
 */
public class InitializationProvider extends ContentProvider {

    @Override
    public final boolean onCreate() {
        Context context = getContext();
        if (context != null) {
            // Many Initializer's expect the `applicationContext` to be non-null. This
            // typically happens when `android:sharedUid` is used. In such cases, we postpone
            // initialization altogether, and rely on lazy init.
            // More context: b/196959015
            Context applicationContext = context.getApplicationContext();
            if (applicationContext != null) {
                // Pass the class context so the right metadata can be read.
                // This is especially important in the context of apps that want to use
                // InitializationProvider in multiple processes.
                // b/183136596#comment18
                AppInitializer.getInstance(context).discoverAndInitialize(getClass());
            } else {
                StartupLogger.w("Deferring initialization because `applicationContext` is null.");
            }
        } else {
            throw new StartupException("Context cannot be null");
        }
        return true;
    }

    @Override
    public final @Nullable Cursor query(
            @NonNull Uri uri,
            String @Nullable [] projection,
            @Nullable String selection,
            String @Nullable [] selectionArgs,
            @Nullable String sortOrder) {
        throw new IllegalStateException("Not allowed.");
    }

    @Override
    public final @Nullable String getType(@NonNull Uri uri) {
        throw new IllegalStateException("Not allowed.");
    }

    @Override
    public final @Nullable Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new IllegalStateException("Not allowed.");
    }

    @Override
    public final int delete(
            @NonNull Uri uri,
            @Nullable String selection,
            String @Nullable [] selectionArgs) {
        throw new IllegalStateException("Not allowed.");
    }

    @Override
    public final int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            String @Nullable [] selectionArgs) {
        throw new IllegalStateException("Not allowed.");
    }
}
