package com.soluna.ktvisual.ocr.multimodal

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.Reasoning
import com.openai.models.ReasoningEffort
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputImage
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.ResponseInputText
import com.openai.models.responses.ResponseStreamEvent
import java.net.URI
import java.time.Duration

/**
 * OpenAI SDK backed OCR client for OpenAI or OpenAI-compatible multimodal gateways.
 *
 * The client uses the official OpenAI Java SDK and the Responses API. Private
 * gateways can still be used when they expose a compatible `/responses`
 * endpoint under [OpenAiCompatibleMultimodalOcrConfig.baseUrl].
 */
class OpenAiCompatibleMultimodalOcrClient(
    private val baseUrl: URI,
    private val model: String,
    private val apiKey: String? = null,
    private val timeout: Duration = Duration.ofSeconds(60),
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val reasoningEffort: String? = null,
    private val stream: Boolean = false,
    private val onStreamEvent: (OpenAiCompatibleStreamEvent) -> Unit = {},
    private val systemPrompt: String = "You are a precise OCR engine for UI screenshots.",
    private val client: OpenAIClient = sdkClient(baseUrl, apiKey, timeout, extraHeaders)
) : MultimodalOcrClient {

    init {
        require(model.isNotBlank()) { "model must not be blank." }
        require(!timeout.isNegative && !timeout.isZero) { "timeout must be positive." }
        require(reasoningEffort == null || reasoningEffort.isNotBlank()) { "reasoningEffort must not be blank." }
        require(systemPrompt.isNotBlank()) { "systemPrompt must not be blank." }
    }

    override fun complete(request: MultimodalOcrRequest): String {
        val params = params(request)
        return try {
            if (stream) {
                completeStreaming(params)
            } else {
                extractMessageContent(client.responses().create(params))
            }
        } catch (error: Exception) {
            throw MultimodalOcrException("Multimodal OCR request failed.", error)
        }
    }

    private fun params(request: MultimodalOcrRequest): ResponseCreateParams {
        val message = ResponseInputItem.Message.builder()
            .role(ResponseInputItem.Message.Role.USER)
            .addContent(ResponseInputText.builder().text(request.prompt).build())
            .addContent(
                ResponseInputImage.builder()
                    .imageUrl(request.dataUrl())
                    .detail(ResponseInputImage.Detail.HIGH)
                    .build()
            )
            .build()

        val builder = ResponseCreateParams.builder()
            .model(model)
            .instructions(systemPrompt)
            .inputOfResponse(listOf(ResponseInputItem.ofMessage(message)))
            .store(false)

        reasoningEffort?.let { effort ->
            builder.reasoning(
                Reasoning.builder()
                    .effort(ReasoningEffort.of(effort))
                    .build()
            )
        }

        return builder.build()
    }

    private fun completeStreaming(params: ResponseCreateParams): String {
        val content = StringBuilder()
        client.responses().createStreaming(params).use { response ->
            response.stream().forEach { event ->
                appendStreamingEvent(event, content)
            }
        }
        if (content.isEmpty()) {
            throw MultimodalOcrException("Multimodal OCR stream did not contain message content.")
        }
        return content.toString()
    }

    private fun appendStreamingEvent(event: ResponseStreamEvent, content: StringBuilder) {
        event.reasoningSummaryTextDelta().ifPresent { delta ->
            if (delta.delta().isNotEmpty()) {
                onStreamEvent(OpenAiCompatibleStreamEvent.Reasoning(delta.delta()))
            }
        }
        event.reasoningTextDelta().ifPresent { delta ->
            if (delta.delta().isNotEmpty()) {
                onStreamEvent(OpenAiCompatibleStreamEvent.Reasoning(delta.delta()))
            }
        }
        event.outputTextDelta().ifPresent { delta ->
            val text = delta.delta()
            content.append(text)
            if (text.isNotEmpty()) {
                onStreamEvent(OpenAiCompatibleStreamEvent.Content(text))
            }
        }
    }

    private fun extractMessageContent(response: Response): String {
        val content = response.output()
            .asSequence()
            .filter { it.isMessage() }
            .flatMap { it.asMessage().content().asSequence() }
            .mapNotNull { part -> part.outputText().map { it.text() }.orElse(null) }
            .joinToString(separator = "\n")

        if (content.isBlank()) {
            throw MultimodalOcrException("Multimodal OCR response did not contain output text.")
        }
        return content
    }

    companion object {
        /**
         * Creates a client from an OpenAI-compatible base URL such as
         * `https://gateway.example.com/v1`.
         */
        fun fromConfig(
            config: OpenAiCompatibleMultimodalOcrConfig,
            onStreamEvent: (OpenAiCompatibleStreamEvent) -> Unit = {}
        ): OpenAiCompatibleMultimodalOcrClient {
            return OpenAiCompatibleMultimodalOcrClient(
                baseUrl = config.baseUrl,
                model = config.model,
                apiKey = config.apiKey,
                timeout = config.timeout,
                extraHeaders = config.extraHeaders,
                reasoningEffort = config.reasoningEffort,
                stream = config.stream,
                onStreamEvent = onStreamEvent,
                systemPrompt = config.systemPrompt
            )
        }

        private fun sdkClient(
            baseUrl: URI,
            apiKey: String?,
            timeout: Duration,
            extraHeaders: Map<String, String>
        ): OpenAIClient {
            val builder = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl.toString().trimEnd('/'))
                .timeout(timeout)
            if (!apiKey.isNullOrBlank()) {
                builder.apiKey(apiKey)
            }
            extraHeaders.forEach { (name, value) ->
                builder.putHeader(name, value)
            }
            return builder.build()
        }
    }
}

sealed interface OpenAiCompatibleStreamEvent {
    data class Reasoning(val text: String) : OpenAiCompatibleStreamEvent
    data class Content(val text: String) : OpenAiCompatibleStreamEvent
}

/**
 * Configuration for [OpenAiCompatibleMultimodalOcrClient].
 *
 * [baseUrl] should point at the OpenAI-compatible `/v1` root; the SDK will call
 * the Responses API below that base URL. [apiKey] is optional for private
 * gateways that authenticate through [extraHeaders] or network policy. Set
 * [reasoningEffort] to values accepted by the target model, such as `"high"`,
 * when the gateway supports reasoning controls. [stream] only changes how the
 * response is transported; the final returned OCR text remains the concatenated
 * model output.
 */
data class OpenAiCompatibleMultimodalOcrConfig(
    val baseUrl: URI,
    val apiKey: String? = null,
    val model: String,
    val timeout: Duration = Duration.ofSeconds(60),
    val extraHeaders: Map<String, String> = emptyMap(),
    val reasoningEffort: String? = null,
    val stream: Boolean = false,
    val systemPrompt: String = "You are a precise OCR engine for UI screenshots."
) {
    init {
        require(model.isNotBlank()) { "model must not be blank." }
        require(!timeout.isNegative && !timeout.isZero) { "timeout must be positive." }
        require(reasoningEffort == null || reasoningEffort.isNotBlank()) { "reasoningEffort must not be blank." }
        require(systemPrompt.isNotBlank()) { "systemPrompt must not be blank." }
    }
}
