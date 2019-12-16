/*
 * Copyright 2018 MovingBlocks
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
package org.destinationsol;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.pipe.Pipe;
import com.jagrosh.discordipc.entities.pipe.WindowsPipe;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;

/**
 * Manages the discord rpc connection
 */
public class RPCManager implements Runnable, IPCListener {

    private static RPCManager instance;
    private static final Logger logger = LoggerFactory.getLogger(RPCManager.class);
    private static final String DISCORD_APP_LARGE_IMAGE = "mainmenubg";
    private static final long DISCORD_CLIENT_ID = 522438873742311427L;
    private static final int RECONNECT_MAX_TRIES = 5;
    private static final int RECONNECT_TIMEOUT = 2000;

    private IPCClient client;
    private Thread thread;
    private int reconnectTries;
    private boolean firstConnect = true;
    private boolean enabled = true;
    private boolean alive;
    private boolean ready;
    private boolean dontTryToConnect;
    private RichPresence lastPresence;

    /**
     * Initial the rpc connection
     */
    public RPCManager() {
        if (instance != null) {
            throw new RuntimeException("More then one instance for the rpc manager.");
        }
        instance = this;
        client = new IPCClient(DISCORD_CLIENT_ID);
        client.setListener(this);
        thread = new Thread(this);
        thread.setName("DISCORD-RPC-THREAD");
        alive = true;
        thread.start();
        disableLogger(IPCClient.class);
        disableLogger(WindowsPipe.class);
        disableLogger(Pipe.class);
    }

    /**
     * Send a presence to the client in a safey way
     *
     * @param presence The presence to send
     */
    public void sendRichPresence(RichPresence presence) {
        if (presence == null) {
            return;
        }
        lastPresence = presence;
        if (ready) {
            client.sendRichPresence(presence);
        }
    }

    /**
     * Handles the ready event ( aka connect event )
     *
     * @param ipcClient Our ipc client
     */
    @Override
    public void onReady(IPCClient ipcClient) {
        if (!enabled) {
            ipcClient.close();
            return;
        }
        ready = true;
        if (firstConnect) {
            logger.info("Successfully! Connected to the rpc.");
            RichPresence.Builder builder = new RichPresence.Builder();
            builder.setStartTimestamp(OffsetDateTime.now());
            builder.setLargeImage(DISCORD_APP_LARGE_IMAGE);
            builder.setStartTimestamp(OffsetDateTime.now());
            RichPresence presence = builder.build();
            sendRichPresence(presence);
        } else {
            logger.info("Successfully! Reconnected to the rpc.");
        }
    }

    /**
     * Handles the disconnect event
     *
     * @param ipcClient Our ipc client
     * @param throwable The error that made us to disconnect
     */
    @Override
    public void onDisconnect(IPCClient ipcClient, Throwable throwable) {
        ready = false;
    }

    @Override
    public void run() {
        while (alive) {
            try {

                if (firstConnect) {
                    client.connect();
                    Thread.sleep(2000L); // short time to check if it connected or not
                    if (ready) {
                        enabled = true;
                    }
                    firstConnect = false; // let the reconnect do it's work
                    continue;
                }

                if (!enabled || dontTryToConnect) {
                    Thread.sleep(1);
                    continue;
                }

                if (ready) { // ping
                    if (lastPresence != null) {
                        client.sendRichPresence(lastPresence);
                        Thread.sleep(5000);
                        continue;
                    } else {
                        Thread.sleep(1);
                        continue;
                    }
                } else { // reconnect
                    int timeout = reconnectTries * RECONNECT_TIMEOUT;
                    logger.info(String.format("Warning! Trying to reconnect in %s seconds.", timeout));
                    Thread.sleep(timeout);
                    client.connect();
                    if (ready) {
                        reconnectTries = 0;
                        continue;
                    }
                    reconnectTries++;
                    if (reconnectTries > RECONNECT_MAX_TRIES) {
                        logger.info("Failed! To connect to the rpc.");
                        dontTryToConnect = true;
                    }
                }
            } catch (InterruptedException ex) { // Ignore the interrupted exceptions
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * To avoid from spamming from some classes
     * because of reconnect functionality
     *
     * @param clazz The class to disable the log from it
     */
    private void disableLogger(Class<?> clazz) {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (loggerFactory instanceof ch.qos.logback.classic.LoggerContext) {
            LoggerContext loggerContext = (LoggerContext) loggerFactory;
            Logger classLogger = loggerContext.getLogger(clazz);
            ((ch.qos.logback.classic.Logger) classLogger).setLevel(Level.OFF);
        }
    }

    /**
     * Get the rpc manager instance
     *
     * @return RPCManager
     */
    public static RPCManager getInstance() {
        return instance;
    }

    /**
     * Set a state a game with/out timestamp
     *
     * @param state The state of the game that the manager will change to it
     * @param timestamp Show the timestamp ( now Time ) or not
     */
    public static void setState(String state, boolean timestamp) {
        if (getInstance() == null) {
            return;
        }
        RichPresence.Builder builder = new RichPresence.Builder();
        builder.setState(state);
        builder.setLargeImage(DISCORD_APP_LARGE_IMAGE);
        if (timestamp) {
            builder.setStartTimestamp(OffsetDateTime.now());
        }
        RichPresence presence = builder.build();
        getInstance().sendRichPresence(presence);
    }

    /**
     * To enable the RPC
     */
    public static void enable() {
        if (getInstance() == null) {
            return;
        }
        getInstance().enabled = true;
        getInstance().firstConnect = true;
        getInstance().dontTryToConnect = false;
    }

    /**
     * To disable the RPC
     */
    public static void disable() {
        if (getInstance() == null) {
            return;
        }
        getInstance().enabled = false;
        getInstance().dontTryToConnect = true;
        getInstance().reconnectTries = 0;
        if (getInstance().ready) {
            getInstance().client.close();
            getInstance().ready = false;
        }
    }

    /**
     * To shutdown the RPC
     */
    public static void shutdown() {
        if (getInstance() == null) {
            return;
        }
        getInstance().enabled = false;
        if (getInstance().ready) {
            getInstance().client.close();
        }
        getInstance().alive = false;
    }

    /**
     * Get if the rpc is on or off
     *
     * @return boolean
     */
    public static boolean isEnable() {
        if (getInstance() == null) {
            return false;
        }
        return getInstance().enabled;
    }
}