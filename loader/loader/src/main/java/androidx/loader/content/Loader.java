/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.loader.content;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;

import androidx.annotation.MainThread;
import androidx.loader.app.LoaderManager;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Static library support version of the framework's {@link android.content.Loader}.
 * Used to write apps that run on platforms prior to Android 3.0.  When running
 * on Android 3.0 or above, this implementation is still used; it does not try
 * to switch to the framework's implementation.  See the framework SDK
 * documentation for a class overview.
 */
public class Loader<D> {
    private int mId;
    private OnLoadCompleteListener<D> mListener;
    private OnLoadCanceledListener<D> mOnLoadCanceledListener;
    private Context mContext;
    private boolean mStarted = false;
    private boolean mAbandoned = false;
    private boolean mReset = true;
    private boolean mContentChanged = false;
    private boolean mProcessingChange = false;

    /**
     * An implementation of a ContentObserver that takes care of connecting
     * it to the Loader to have the loader re-load its data when the observer
     * is told it has changed.  You do not normally need to use this yourself;
     * it is used for you by {@link CursorLoader} to take care of executing
     * an update when the cursor's backing data changes.
     */
    public final class ForceLoadContentObserver extends ContentObserver {
        @SuppressWarnings("deprecation")
        public ForceLoadContentObserver() {
            super(new Handler());
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            onContentChanged();
        }
    }

    /**
     * Interface that is implemented to discover when a Loader has finished
     * loading its data.  You do not normally need to implement this yourself;
     * it is used in the implementation of {@link LoaderManager}
     * to find out when a Loader it is managing has completed so that this can
     * be reported to its client.  This interface should only be used if a
     * Loader is not being used in conjunction with LoaderManager.
     */
    public interface OnLoadCompleteListener<D> {
        /**
         * Called on the thread that created the Loader when the load is complete.
         *
         * @param loader the loader that completed the load
         * @param data the result of the load
         */
        void onLoadComplete(@NonNull Loader<D> loader, @Nullable D data);
    }

    /**
     * Interface that is implemented to discover when a Loader has been canceled
     * before it finished loading its data.  You do not normally need to implement
     * this yourself; it is used in the implementation of {@link LoaderManager}
     * to find out when a Loader it is managing has been canceled so that it
     * can schedule the next Loader.  This interface should only be used if a
     * Loader is not being used in conjunction with LoaderManager.
     */
    public interface OnLoadCanceledListener<D> {
        /**
         * Called on the thread that created the Loader when the load is canceled.
         *
         * @param loader the loader that canceled the load
         */
        void onLoadCanceled(@NonNull Loader<D> loader);
    }

    /**
     * Stores away the application context associated with context.
     * Since Loaders can be used across multiple activities it's dangerous to
     * store the context directly; always use {@link #getContext()} to retrieve
     * the Loader's Context, don't use the constructor argument directly.
     * The Context returned by {@link #getContext} is safe to use across
     * Activity instances.
     *
     * @param context used to retrieve the application context.
     */
    public Loader(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * Sends the result of the load to the registered listener. Should only be called by subclasses.
     *
     * Must be called from the process's main thread.
     *
     * @param data the result of the load
     */
    @MainThread
    public void deliverResult(@Nullable D data) {
        if (mListener != null) {
            mListener.onLoadComplete(this, data);
        }
    }

    /**
     * Informs the registered {@link OnLoadCanceledListener} that the load has been canceled.
     * Should only be called by subclasses.
     *
     * Must be called from the process's main thread.
     */
    @MainThread
    public void deliverCancellation() {
        if (mOnLoadCanceledListener != null) {
            mOnLoadCanceledListener.onLoadCanceled(this);
        }
    }

    /**
     * @return an application context retrieved from the Context passed to the constructor.
     */
    public @NonNull Context getContext() {
        return mContext;
    }

    /**
     * @return the ID of this loader
     */
    public int getId() {
        return mId;
    }

    /**
     * Registers a class that will receive callbacks when a load is complete.
     * The callback will be called on the process's main thread so it's safe to
     * pass the results to widgets.
     *
     * <p>Must be called from the process's main thread.
     */
    @MainThread
    public void registerListener(int id, @NonNull OnLoadCompleteListener<D> listener) {
        if (mListener != null) {
            throw new IllegalStateException("There is already a listener registered");
        }
        mListener = listener;
        mId = id;
    }

    /**
     * Remove a listener that was previously added with {@link #registerListener}.
     *
     * Must be called from the process's main thread.
     */
    @MainThread
    public void unregisterListener(@NonNull OnLoadCompleteListener<D> listener) {
        if (mListener == null) {
            throw new IllegalStateException("No listener register");
        }
        if (mListener != listener) {
            throw new IllegalArgumentException("Attempting to unregister the wrong listener");
        }
        mListener = null;
    }

    /**
     * Registers a listener that will receive callbacks when a load is canceled.
     * The callback will be called on the process's main thread so it's safe to
     * pass the results to widgets.
     *
     * Must be called from the process's main thread.
     *
     * @param listener The listener to register.
     */
    @MainThread
    public void registerOnLoadCanceledListener(@NonNull OnLoadCanceledListener<D> listener) {
        if (mOnLoadCanceledListener != null) {
            throw new IllegalStateException("There is already a listener registered");
        }
        mOnLoadCanceledListener = listener;
    }

    /**
     * Unregisters a listener that was previously added with
     * {@link #registerOnLoadCanceledListener}.
     *
     * Must be called from the process's main thread.
     *
     * @param listener The listener to unregister.
     */
    @MainThread
    public void unregisterOnLoadCanceledListener(@NonNull OnLoadCanceledListener<D> listener) {
        if (mOnLoadCanceledListener == null) {
            throw new IllegalStateException("No listener register");
        }
        if (mOnLoadCanceledListener != listener) {
            throw new IllegalArgumentException("Attempting to unregister the wrong listener");
        }
        mOnLoadCanceledListener = null;
    }

    /**
     * Return whether this load has been started.  That is, its {@link #startLoading()}
     * has been called and no calls to {@link #stopLoading()} or
     * {@link #reset()} have yet been made.
     */
    public boolean isStarted() {
        return mStarted;
    }

    /**
     * Return whether this loader has been abandoned.  In this state, the
     * loader <em>must not</em> report any new data, and <em>must</em> keep
     * its last reported data valid until it is finally reset.
     */
    public boolean isAbandoned() {
        return mAbandoned;
    }

    /**
     * Return whether this load has been reset.  That is, either the loader
     * has not yet been started for the first time, or its {@link #reset()}
     * has been called.
     */
    public boolean isReset() {
        return mReset;
    }

    /**
     * This function will normally be called for you automatically by
     * {@link LoaderManager} when the associated fragment/activity
     * is being started.  When using a Loader with {@link LoaderManager},
     * you <em>must not</em> call this method yourself, or you will conflict
     * with its management of the Loader.
     *
     * Starts an asynchronous load of the Loader's data. When the result
     * is ready the callbacks will be called on the process's main thread.
     * If a previous load has been completed and is still valid
     * the result may be passed to the callbacks immediately.
     * The loader will monitor the source of
     * the data set and may deliver future callbacks if the source changes.
     * Calling {@link #stopLoading} will stop the delivery of callbacks.
     *
     * <p>This updates the Loader's internal state so that
     * {@link #isStarted()} and {@link #isReset()} will return the correct
     * values, and then calls the implementation's {@link #onStartLoading()}.
     *
     * <p>Must be called from the process's main thread.
     */
    @MainThread
    public final void startLoading() {
        mStarted = true;
        mReset = false;
        mAbandoned = false;
        onStartLoading();
    }

    /**
     * Subclasses must implement this to take care of loading their data,
     * as per {@link #startLoading()}.  This is not called by clients directly,
     * but as a result of a call to {@link #startLoading()}.
     * This will always be called from the process's main thread.
     */
    @MainThread
    protected void onStartLoading() {
    }

    /**
     * Attempt to cancel the current load task.
     * Must be called on the main thread of the process.
     *
     * <p>Cancellation is not an immediate operation, since the load is performed
     * in a background thread.  If there is currently a load in progress, this
     * method requests that the load be canceled, and notes this is the case;
     * once the background thread has completed its work its remaining state
     * will be cleared.  If another load request comes in during this time,
     * it will be held until the canceled load is complete.
     *
     * @return Returns <tt>false</tt> if the task could not be canceled,
     * typically because it has already completed normally, or
     * because {@link #startLoading()} hasn't been called; returns
     * <tt>true</tt> otherwise.  When <tt>true</tt> is returned, the task
     * is still running and the {@link OnLoadCanceledListener} will be called
     * when the task completes.
     */
    @MainThread
    public boolean cancelLoad() {
        return onCancelLoad();
    }

    /**
     * Subclasses must implement this to take care of requests to {@link #cancelLoad()}.
     * This will always be called from the process's main thread.
     *
     * @return Returns <tt>false</tt> if the task could not be canceled,
     * typically because it has already completed normally, or
     * because {@link #startLoading()} hasn't been called; returns
     * <tt>true</tt> otherwise.  When <tt>true</tt> is returned, the task
     * is still running and the {@link OnLoadCanceledListener} will be called
     * when the task completes.
     */
    @MainThread
    protected boolean onCancelLoad() {
        return false;
    }

    /**
     * Force an asynchronous load. Unlike {@link #startLoading()} this will ignore a previously
     * loaded data set and load a new one.  This simply calls through to the
     * implementation's {@link #onForceLoad()}.  You generally should only call this
     * when the loader is started -- that is, {@link #isStarted()} returns true.
     *
     * <p>Must be called from the process's main thread.
     */
    @MainThread
    public void forceLoad() {
        onForceLoad();
    }

    /**
     * Subclasses must implement this to take care of requests to {@link #forceLoad()}.
     * This will always be called from the process's main thread.
     */
    @MainThread
    protected void onForceLoad() {
    }

    /**
     * This function will normally be called for you automatically by
     * {@link LoaderManager} when the associated fragment/activity
     * is being stopped.  When using a Loader with {@link LoaderManager},
     * you <em>must not</em> call this method yourself, or you will conflict
     * with its management of the Loader.
     *
     * <p>Stops delivery of updates until the next time {@link #startLoading()} is called.
     * Implementations should <em>not</em> invalidate their data at this point --
     * clients are still free to use the last data the loader reported.  They will,
     * however, typically stop reporting new data if the data changes; they can
     * still monitor for changes, but must not report them to the client until and
     * if {@link #startLoading()} is later called.
     *
     * <p>This updates the Loader's internal state so that
     * {@link #isStarted()} will return the correct
     * value, and then calls the implementation's {@link #onStopLoading()}.
     *
     * <p>Must be called from the process's main thread.
     */
    @MainThread
    public void stopLoading() {
        mStarted = false;
        onStopLoading();
    }

    /**
     * Subclasses must implement this to take care of stopping their loader,
     * as per {@link #stopLoading()}.  This is not called by clients directly,
     * but as a result of a call to {@link #stopLoading()}.
     * This will always be called from the process's main thread.
     */
    @MainThread
    protected void onStopLoading() {
    }

    /**
     * This function will normally be called for you automatically by
     * {@link LoaderManager} when restarting a Loader.  When using
     * a Loader with {@link LoaderManager},
     * you <em>must not</em> call this method yourself, or you will conflict
     * with its management of the Loader.
     *
     * Tell the Loader that it is being abandoned.  This is called prior
     * to {@link #reset} to have it retain its current data but not report
     * any new data.
     *
     * <p>Must be called from the process's main thread.
     */
    @MainThread
    public void abandon() {
        mAbandoned = true;
        onAbandon();
    }

    /**
     * Subclasses implement this to take care of being abandoned.  This is
     * an optional intermediate state prior to {@link #onReset()} -- it means that
     * the client is no longer interested in any new data from the loader,
     * so the loader must not report any further updates.  However, the
     * loader <em>must</em> keep its last reported data valid until the final
     * {@link #onReset()} happens.  You can retrieve the current abandoned
     * state with {@link #isAbandoned}.
     * This will always be called from the process's main thread.
     */
    @MainThread
    protected void onAbandon() {
    }

    /**
     * This function will normally be called for you automatically by
     * {@link LoaderManager} when destroying a Loader.  When using
     * a Loader with {@link LoaderManager},
     * you <em>must not</em> call this method yourself, or you will conflict
     * with its management of the Loader.
     *
     * Resets the state of the Loader.  The Loader should at this point free
     * all of its resources, since it may never be called again; however, its
     * {@link #startLoading()} may later be called at which point it must be
     * able to start running again.
     *
     * <p>This updates the Loader's internal state so that
     * {@link #isStarted()} and {@link #isReset()} will return the correct
     * values, and then calls the implementation's {@link #onReset()}.
     *
     * <p>Must be called from the process's main thread.
     */
    @MainThread
    public void reset() {
        onReset();
        mReset = true;
        mStarted = false;
        mAbandoned = false;
        mContentChanged = false;
        mProcessingChange = false;
    }

    /**
     * Subclasses must implement this to take care of resetting their loader,
     * as per {@link #reset()}.  This is not called by clients directly,
     * but as a result of a call to {@link #reset()}.
     * This will always be called from the process's main thread.
     */
    @MainThread
    protected void onReset() {
    }

    /**
     * Take the current flag indicating whether the loader's content had
     * changed while it was stopped.  If it had, true is returned and the
     * flag is cleared.
     */
    public boolean takeContentChanged() {
        boolean res = mContentChanged;
        mContentChanged = false;
        mProcessingChange |= res;
        return res;
    }

    /**
     * Commit that you have actually fully processed a content change that
     * was returned by {@link #takeContentChanged}.  This is for use with
     * {@link #rollbackContentChanged()} to handle situations where a load
     * is cancelled.  Call this when you have completely processed a load
     * without it being cancelled.
     */
    public void commitContentChanged() {
        mProcessingChange = false;
    }

    /**
     * Report that you have abandoned the processing of a content change that
     * was returned by {@link #takeContentChanged()} and would like to rollback
     * to the state where there is again a pending content change.  This is
     * to handle the case where a data load due to a content change has been
     * canceled before its data was delivered back to the loader.
     */
    public void rollbackContentChanged() {
        if (mProcessingChange) {
            onContentChanged();
        }
    }

    /**
     * Called when {@link ForceLoadContentObserver} detects a change.  The
     * default implementation checks to see if the loader is currently started;
     * if so, it simply calls {@link #forceLoad()}; otherwise, it sets a flag
     * so that {@link #takeContentChanged()} returns true.
     *
     * <p>Must be called from the process's main thread.
     */
    @MainThread
    public void onContentChanged() {
        if (mStarted) {
            forceLoad();
        } else {
            // This loader has been stopped, so we don't want to load
            // new data right now...  but keep track of it changing to
            // refresh later if we start again.
            mContentChanged = true;
        }
    }

    /**
     * For debugging, converts an instance of the Loader's data class to
     * a string that can be printed.  Must handle a null data.
     */
    public @NonNull String dataToString(@Nullable D data) {
        StringBuilder sb = new StringBuilder(64);
        if (data == null) {
            sb.append("null");
        } else {
            Class<?> cls = data.getClass();
            sb.append(cls.getSimpleName());
            sb.append("{");
            sb.append(Integer.toHexString(System.identityHashCode(data)));
            sb.append("}");
        }
        return sb.toString();
    }

    @Override
    public @NonNull String toString() {
        StringBuilder sb = new StringBuilder(64);
        Class<?> cls = getClass();
        sb.append(cls.getSimpleName());
        sb.append("{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        sb.append(" id=");
        sb.append(mId);
        sb.append("}");
        return sb.toString();
    }

    /**
     * Print the Loader's state into the given stream.
     *
     * @param prefix Text to print at the front of each line.
     * @param fd The raw file descriptor that the dump is being sent to.
     * @param writer A PrintWriter to which the dump is to be set.
     * @param args Additional arguments to the dump request.
     * @deprecated Consider using {@link LoaderManager#enableDebugLogging(boolean)} to understand
     * the series of operations performed by LoaderManager.
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    public void dump(@NonNull String prefix, @Nullable FileDescriptor fd,
            @NonNull PrintWriter writer, String @Nullable [] args) {
        writer.print(prefix); writer.print("mId="); writer.print(mId);
                writer.print(" mListener="); writer.println(mListener);
        if (mStarted || mContentChanged || mProcessingChange) {
            writer.print(prefix); writer.print("mStarted="); writer.print(mStarted);
                    writer.print(" mContentChanged="); writer.print(mContentChanged);
                    writer.print(" mProcessingChange="); writer.println(mProcessingChange);
        }
        if (mAbandoned || mReset) {
            writer.print(prefix); writer.print("mAbandoned="); writer.print(mAbandoned);
                    writer.print(" mReset="); writer.println(mReset);
        }
    }
}