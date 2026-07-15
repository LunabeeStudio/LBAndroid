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
import android.content.Context
import android.os.Handler
import android.os.Looper
import co.touchlab.kermit.Logger
import com.parse.ParseObject
import com.parse.ParseQuery
import com.parse.livequery.LiveQueryException
import com.parse.livequery.ParseLiveQueryClient
import com.parse.livequery.ParseLiveQueryClientCallbacks
import com.parse.livequery.SubscriptionHandling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    private lateinit var context: Context

    private var parseLiveQueryClient: ParseLiveQueryClient? = null

    private var isConnected = false

    private var isReconnecting = false

    private var handler: Handler = Handler(Looper.getMainLooper())

    /**
     * Scope + job used to await connectivity before reconnecting (replaces the legacy connectivity
     * BroadcastReceiver).
     */
    private val reconnectionScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectionJob: Job? = null

    private var reconnectCallback: Runnable = Runnable { parseLiveQueryClient?.reconnect() }

    /**
     * This block listen the network and reconnect the client if connection is available
     * If we are already connected when entered in this method, the server must be down,
     * so we try to reconnect after a delay
     */
    private fun listenReconnection() {
        isReconnecting = true
        if (LBConnectivityManager.getNetworkState(context).isConnected) {
            handler.postDelayed(reconnectCallback, ServerDownDelayMs)
        } else {
            awaitConnectivityThenReconnect()
        }
    }

    /**
     * Suspend until connectivity is back (collecting [LBConnectivityManager.networkStates]) then
     * reconnect the LiveQuery client. Replaces the legacy connectivity BroadcastReceiver.
     */
    private fun awaitConnectivityThenReconnect() {
        reconnectionJob?.cancel()
        reconnectionJob = reconnectionScope.launch {
            LBConnectivityManager.networkStates(context).first { it.isConnected }
            parseLiveQueryClient?.reconnect()
        }
    }

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
        reconnectionJob?.cancel()
        handler.removeCallbacks(reconnectCallback)
        parseLiveQueryClient?.unsubscribe(query)
    }

    override fun onLiveQueryClientConnected(client: ParseLiveQueryClient?) {
        logger.v("Connected")
        isConnected = true
        isReconnecting = false
    }

    override fun onLiveQueryClientDisconnected(
        client: ParseLiveQueryClient?,
        userInitiated: Boolean,
    ) {
        logger.v("Disconnected")
        isConnected = false
    }

    override fun onLiveQueryError(client: ParseLiveQueryClient?, reason: LiveQueryException?) {
        logger.e("Error ${reason?.localizedMessage}\n${reason?.cause?.localizedMessage}")
    }

    override fun onSocketError(client: ParseLiveQueryClient?, reason: Throwable?) {
        logger.e("Socket error: ${reason?.localizedMessage}\n${reason?.cause?.localizedMessage}")
        listenReconnection()
    }

    companion object {
        private const val ServerDownDelayMs: Long = 15000

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
