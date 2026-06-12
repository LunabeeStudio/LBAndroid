/*
 * Copyright (c) 2026 Lunabee Studio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package studio.lunabee.synchronization.parseroomsyncmanager

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import co.touchlab.kermit.Logger
import com.parse.ParseObject
import com.parse.ParseQuery
import com.parse.livequery.LiveQueryException
import com.parse.livequery.ParseLiveQueryClient
import com.parse.livequery.ParseLiveQueryClientCallbacks
import com.parse.livequery.SubscriptionHandling
import studio.lunabee.logger.LBLogger
import studio.lunabee.synchronization.connectivity.LBConnectivityManager
import java.net.URI
import java.net.URISyntaxException

/**
 * Use this singleton to subscribe at Parse LiveQueries.
 *
 * Persistence-agnostic so this module is self-contained.
 */
class LBParseLiveQueryManager : ParseLiveQueryClientCallbacks {

    /**
     * Must be the Application context
     */
    private lateinit var context: Context

    /**
     * The LiveQuery client used for all the app
     */
    private var parseLiveQueryClient: ParseLiveQueryClient? = null

    /**
     * Is the LiveQuery client connected or not
     */
    private var isConnected = false

    /**
     * Is the LiveQuery client already trying to reconnect
     */
    private var isReconnecting = false

    /**
     * Used for the reconnection
     */
    private var handler: Handler = Handler(Looper.getMainLooper())

    /**
     * Used for the reconnection
     */
    private var connectivityManager: LBConnectivityManager? = null

    /**
     * Used for the reconnection
     */
    private var reconnectCallback: Runnable = Runnable { parseLiveQueryClient?.reconnect() }
    /*=================
     * Private methods
     =================*/

    /**
     * This block listen the network and reconnect the client if connection is available
     * If we are already connected when entered in this method, the server must be down,
     * so we try to reconnect after a delay
     */
    private fun listenReconnection() {
        isReconnecting = true
        if (LBConnectivityManager.getNetworkState(context).isConnected) {
            handler.postDelayed(reconnectCallback, SERVER_DOWN_DELAY_MS)
        } else {
            connectivityManager?.startListening(context)
        }
    }

    /**
     * Init the connectivityManager and the logic for the listener
     */
    private fun initConnectivityManager() {
        connectivityManager?.stopListening(context)
        connectivityManager = LBConnectivityManager()
        connectivityManager?.listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                context?.let {
                    if (LBConnectivityManager.getNetworkState(context).isConnected) {
                        parseLiveQueryClient?.reconnect()
                        connectivityManager?.stopListening(context)
                    }
                }
            }
        }
    }
    /*=================
     * Public methods
    =================*/

    /**
     * Init the LBParseLiveQueryManager
     * **WARNING** Must be called before any usage
     * @param context: Must be the Application context
     * @param parseLiveQueryURL: the LiveQuery url used to build the client
     */
    fun init(context: Context, parseLiveQueryURL: String) {
        this.context = context
        try {
            parseLiveQueryClient = ParseLiveQueryClient.Factory.getClient(URI(parseLiveQueryURL))
            parseLiveQueryClient?.registerListener(this)
            parseLiveQueryClient?.connectIfNeeded()
            initConnectivityManager()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            logger.e(e.localizedMessage ?: "")
        }
    }

    /**
     * Subscribe for a LiveQuery
     * @param query: the ParseQuery to use for the subscription
     * @return the subscription handling to catch event like create, update, etc..
     */
    fun subscribe(query: ParseQuery<ParseObject>): SubscriptionHandling<ParseObject>? {
        logger.v("Subscribe a new LiveQuery for ${query.className}")
        return parseLiveQueryClient?.subscribe(query)
    }

    /**
     * Unsubscribe a LiveQuery
     * @param query: Must be the same instance of ParseQuery you used for subscription
     */
    fun unsubscribe(query: ParseQuery<ParseObject>) {
        logger.v("Unsubscribe LiveQuery for ${query.className}")
        isReconnecting = false
        handler.removeCallbacks(reconnectCallback)
        parseLiveQueryClient?.unsubscribe(query)
    }
    /*===================
     * Overridden methods
     ===================*/

    /**
     * Called when LiveQuery client is connected
     */
    override fun onLiveQueryClientConnected(client: ParseLiveQueryClient?) {
        logger.v("Connected")
        isConnected = true
        isReconnecting = false
    }

    /**
     * Called when LiveQuery client is disconnected
     */
    override fun onLiveQueryClientDisconnected(
        client: ParseLiveQueryClient?,
        userInitiated: Boolean,
    ) {
        logger.v("Disconnected")
        isConnected = false
    }

    /**
     * Called when LiveQuery get errors
     */
    override fun onLiveQueryError(client: ParseLiveQueryClient?, reason: LiveQueryException?) {
        logger.e("Error ${reason?.localizedMessage}\n${reason?.cause?.localizedMessage}")
    }

    /**
     * Called when LiveQuery get socket error, @see {@code listenReconnection()}
     */
    override fun onSocketError(client: ParseLiveQueryClient?, reason: Throwable?) {
        logger.e("Socket error: ${reason?.localizedMessage}\n${reason?.cause?.localizedMessage}")
        listenReconnection()
    }

    companion object {
        /**
         * The delay before reconnect if server is down
         */
        private const val SERVER_DOWN_DELAY_MS: Long = 15000

        /**
         * Private instance for singleton
         */
        @SuppressLint("StaticFieldLeak")
        private val internInstance = LBParseLiveQueryManager()

        /**
         * Public instance for singleton
         * Reconnect the client if it was disconnected
         */
        val instance: LBParseLiveQueryManager
            get() {
                if (!internInstance.isConnected && !internInstance.isReconnecting) {
                    internInstance.parseLiveQueryClient?.reconnect()
                }
                return internInstance
            }
    }
}

private val logger: Logger = LBLogger.get<LBParseLiveQueryManager>()
