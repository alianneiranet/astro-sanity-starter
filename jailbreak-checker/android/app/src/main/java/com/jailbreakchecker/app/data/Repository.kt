package com.jailbreakchecker.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jailbreakchecker.app.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant

/**
 * Source of truth for the UI.
 *
 * The bundled catalog (assets/consoles.json) is always available, so the app works
 * offline and without Firebase. When Firebase is configured, Firestore documents written
 * by the hourly AI updater override the bundled entries with the same id.
 */
class Repository(private val context: Context) {

    val bundled: Catalog by lazy {
        val text = context.assets.open("consoles.json").bufferedReader().use { it.readText() }
        json.decodeFromString(Catalog.serializer(), text)
    }

    private val firestore: FirebaseFirestore? by lazy {
        if (!BuildConfig.FIREBASE_CONFIGURED) return@lazy null
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
            if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseFirestore.getInstance()
        }.onFailure { Log.w(TAG, "Firebase unavailable, using bundled data", it) }.getOrNull()
    }

    val isOnlineEnabled: Boolean get() = firestore != null

    fun brands(): Flow<List<Brand>> = collection("brands", Brand.serializer()) { remote ->
        mergeById(bundled.brands, remote) { it.id }.sortedBy { it.order }
    }

    fun consoles(): Flow<List<ConsoleInfo>> = collection("consoles", ConsoleInfo.serializer()) { remote ->
        mergeById(bundled.consoles, remote) { it.id }
    }

    fun news(limit: Long = 60): Flow<List<NewsItem>> {
        val db = firestore ?: return flowOf(emptyList())
        return callbackFlow {
            val registration = db.collection("news")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "news listener", error)
                        return@addSnapshotListener
                    }
                    val items = snapshot?.documents.orEmpty().mapNotNull { doc ->
                        decode(doc, NewsItem.serializer())?.copy(id = doc.id)
                    }
                    trySend(items)
                }
            awaitClose { registration.remove() }
        }
    }

    fun updaterMeta(): Flow<UpdaterMeta?> {
        val db = firestore ?: return flowOf(null)
        return callbackFlow {
            val registration = db.collection("meta").document("status")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    trySend(snapshot?.let { decode(it, UpdaterMeta.serializer()) })
                }
            awaitClose { registration.remove() }
        }
    }

    private fun <T> collection(
        name: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        combine: (List<T>) -> List<T>,
    ): Flow<List<T>> {
        val db = firestore ?: return flowOf(combine(emptyList()))
        return callbackFlow {
            trySend(combine(emptyList()))
            val registration = db.collection(name).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "$name listener", error)
                    return@addSnapshotListener
                }
                val remote = snapshot?.documents.orEmpty().mapNotNull { decode(it, serializer) }
                trySend(combine(remote))
            }
            awaitClose { registration.remove() }
        }
    }

    private fun <T> decode(doc: DocumentSnapshot, serializer: kotlinx.serialization.KSerializer<T>): T? =
        runCatching { json.decodeFromJsonElement(serializer, toJson(doc.data ?: return null)) }
            .onFailure { Log.w(TAG, "Bad document ${doc.reference.path}", it) }
            .getOrNull()

    companion object {
        private const val TAG = "Repository"

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

        fun <T> mergeById(base: List<T>, overrides: List<T>, id: (T) -> String): List<T> {
            val remote = overrides.associateBy(id)
            val merged = base.map { remote[id(it)] ?: it }
            val extra = overrides.filter { o -> base.none { id(it) == id(o) } }
            return merged + extra
        }

        /** Converts Firestore values (maps, lists, Timestamps...) into a JsonElement tree. */
        fun toJson(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Timestamp -> JsonPrimitive(Instant.ofEpochSecond(value.seconds, value.nanoseconds.toLong()).toString())
            is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
            is List<*> -> JsonArray(value.map { toJson(it) })
            else -> JsonPrimitive(value.toString())
        }
    }
}
