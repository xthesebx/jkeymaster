/*
 * Copyright (c) 2011 Denis Tulskiy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * version 3 along with this work.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tulskiy.keymaster.common;

import com.sun.jna.Platform;
import com.tulskiy.keymaster.osx.CarbonProvider;
import com.tulskiy.keymaster.windows.WindowsProvider;
import com.tulskiy.keymaster.x11.X11Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.Closeable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main interface to global hotkey providers
 * <br>
 * Author: Denis Tulskiy
 * Date: 6/12/11
 */
public abstract class Provider implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Provider.class);

    private boolean useSwingEventQueue;

    /**
     * Get global hotkey provider for current platform
     *
     * @param useSwingEventQueue whether the provider should be using Swing Event queue or a regular thread
     * @return new instance of Provider, or null if platform is not supported
     * @see X11Provider
     * @see WindowsProvider
     * @see CarbonProvider
     */
    public static Provider getCurrentProvider(boolean useSwingEventQueue) {
        Provider provider;
        if (Platform.isX11()) {
            provider = new X11Provider();
        } else if (Platform.isWindows()) {
            provider = new WindowsProvider();
        } else if (Platform.isMac()) {
            provider = new CarbonProvider();
        } else {
            LOGGER.warn("No suitable provider for " + System.getProperty("os.name"));
            return null;
        }
        provider.setUseSwingEventQueue(useSwingEventQueue);
        provider.init();
        return provider;

    }

    private ExecutorService eventQueue;


    /**
     * Initialize provider. Starts main thread that will listen to hotkey events
     */
    protected abstract void init();

    /**
     * Stop the provider. Stops main thread and frees any resources.
     * <br>
     * all hotkeys should be reset before calling this method
     *
     * @see Provider#reset()
     */
    public void stop() {
        if (eventQueue != null)
            eventQueue.shutdown();
    }

    /**
     * Reset all hotkey listeners
     */
    public abstract void reset();

    /**
     * Frees all resources by stopping the provider after resetting all hotkey listeners.
     */
    public void close() {
        reset();
        stop();
    }
    
    /**
     * Determines whether the provider thread is still running. If the thread
     * isn't running it may have been intentionally stopped via stop() or an
     * unhandled error may have occured. If the thread isn't running this
     * instance can't be used anymore. If the thread is running, it can probably
     * still be used, however it might also just be about to stop.
     * 
     * @return
     */
    public abstract boolean isRunning();

    /**
     * Register a global hotkey. Only keyCode and modifiers fields are respected
     *
     * @param keyCode  KeyStroke to register
     * @param listener listener to be notified of hotkey events
     * @see KeyStroke
     */
    public abstract void register(KeyStroke keyCode, HotKeyListener listener);

    /**
     * Register a media hotkey. Currently supported media keys are:
     * <br>
     * <ul>
     * <li>Play/Pause</li>
     * <li>Stop</li>
     * <li>Next track</li>
     * <li>Previous Track</li>
     * </ul>
     *
     * @param mediaKey media key to register
     * @param listener listener to be notified of hotkey events
     * @see MediaKey
     */
    public abstract void register(MediaKey mediaKey, HotKeyListener listener);

    /**
     * Unregister a global hotkey. Only keyCode and modifiers fields are respected
     *
     * @param keyCode  KeyStroke to unregister
     * @see KeyStroke
     */
    public abstract void unregister(KeyStroke keyCode);

    /**
     * Unregister a media hotkey. Currently supported media keys are:
     * <br>
     * <ul>
     * <li>Play/Pause</li>
     * <li>Stop</li>
     * <li>Next track</li>
     * <li>Previous Track</li>
     * </ul>
     *
     * @param mediaKey media key to unregister
     * @see MediaKey
     */
    public abstract void unregister(MediaKey mediaKey);

    /**
     * Helper method fro providers to fire hotkey event in a separate thread
     *
     * @param hotKey hotkey to fire
     */
    protected void fireEvent(HotKey hotKey) {
        HotKeyEvent event = new HotKeyEvent(hotKey);
        if (useSwingEventQueue) {
            SwingUtilities.invokeLater(event);
        } else {
            if (eventQueue == null) {
                eventQueue = Executors.newSingleThreadExecutor();
            }
            eventQueue.execute(event);
        }
    }

    public void setUseSwingEventQueue(boolean useSwingEventQueue) {
        this.useSwingEventQueue = useSwingEventQueue;
    }

    private class HotKeyEvent implements Runnable {
        private HotKey hotKey;

        private HotKeyEvent(HotKey hotKey) {
            this.hotKey = hotKey;
        }

        public void run() {
            hotKey.listener.onHotKey(hotKey);
        }
    }

}
