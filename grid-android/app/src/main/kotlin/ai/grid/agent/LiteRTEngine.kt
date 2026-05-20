package ai.grid.agent

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * On-device LLM inference via Google AI Edge LiteRT-LM.
 *
 * Keeps the Engine (model weights) loaded between calls so only the first
 * inference pays the load cost. Each generation call creates a fresh
 * Conversation, making CLUAgent fully responsible for prompt/history.
 * GPU backend is attempted first; falls back to CPU automatically.
 */
class LiteRTEngine {

    companion object {
        private const val TAG             = "LiteRTEngine"
        private const val MAX_TOKENS      = 2048
        private const val TIMEOUT_SECONDS = 120L
    }

    @Volatile private var engine:          Engine? = null
    @Volatile private var loadedModelPath: String? = null

    val isReady: Boolean get() = engine != null

    /**
     * Load model weights from [modelPath]. Idempotent — no-op when the same
     * path is already loaded. Must be called from a background thread.
     */
    fun loadModelBlocking(modelPath: String): Result<Unit> {
        if (loadedModelPath == modelPath && engine != null) return Result.success(Unit)
        closeEngine()

        return try {
            val file = File(modelPath)
            if (!file.exists()) return Result.failure(Exception("Model not found: $modelPath"))

            Log.i(TAG, "Loading ${file.name} (${file.length() / 1_000_000}MB)")

            val eng = try {
                Engine(
                    EngineConfig(
                        modelPath    = modelPath,
                        backend      = Backend.GPU(),
                        maxNumTokens = MAX_TOKENS,
                    )
                ).also { it.initialize() }
            } catch (_: Exception) {
                Log.w(TAG, "GPU backend unavailable, falling back to CPU")
                Engine(
                    EngineConfig(
                        modelPath    = modelPath,
                        backend      = Backend.CPU(),
                        maxNumTokens = MAX_TOKENS / 2,
                    )
                ).also { it.initialize() }
            }

            engine          = eng
            loadedModelPath = modelPath
            Log.i(TAG, "Loaded: ${file.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Load failed", e)
            Result.failure(e)
        }
    }

    /**
     * Run [prompt] through a fresh Conversation and return the full response.
     * History management is the caller's responsibility — build the complete
     * context into [prompt] before calling. Must be called from a background thread.
     */
    fun generateOnce(prompt: String): Result<String> {
        val eng = engine ?: return Result.failure(Exception("No model loaded"))

        val conv = try {
            eng.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8)
                )
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return try {
            val sb    = StringBuilder()
            val latch = CountDownLatch(1)
            var error: Throwable? = null

            conv.sendMessageAsync(
                Contents.of(listOf(Content.Text(prompt))),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        val chunk = message.toString()
                        if (chunk.isNotEmpty() && !chunk.startsWith("<ctrl")) sb.append(chunk)
                    }
                    override fun onDone()                   = latch.countDown()
                    override fun onError(t: Throwable) { error = t; latch.countDown() }
                }
            )

            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            try { conv.close() } catch (_: Exception) {}

            error?.let { return Result.failure(Exception(it.message ?: "Inference failed")) }
            Result.success(sb.toString())
        } catch (e: Exception) {
            try { conv.close() } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    fun shutdown() = closeEngine()

    private fun closeEngine() {
        engine          = null
        loadedModelPath = null
    }
}
