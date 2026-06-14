package com.soluna.ktvisual.ocr.multimodal

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAiCompatibleMultimodalOcrClientTest {

    @Test
    fun `complete posts chat request and returns message content`() {
        var capturedBody = ""
        var capturedAuthorization: String? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/responses") { exchange ->
            capturedBody = exchange.requestBody.bufferedReader().use { it.readText() }
            capturedAuthorization = exchange.requestHeaders.getFirst("Authorization")
            exchange.respond(responseJson("""{"texts":[{"text":"Login","bounds":{"x":0,"y":0,"width":1,"height":1}}]}"""))
        }
        server.start()

        try {
            val client = OpenAiCompatibleMultimodalOcrClient(
                baseUrl = URI.create("http://127.0.0.1:${server.address.port}"),
                model = "vision-model",
                apiKey = "token",
                timeout = Duration.ofSeconds(2)
            )

            val content = client.complete(
                MultimodalOcrRequest(
                    imageBytes = byteArrayOf(1, 2, 3),
                    width = 2,
                    height = 2,
                    mimeType = "image/png",
                    prompt = "Read text"
                )
            )

            assertEquals("""{"texts":[{"text":"Login","bounds":{"x":0,"y":0,"width":1,"height":1}}]}""", content)
            assertEquals("Bearer token", capturedAuthorization)
            assertTrue(capturedBody.contains("\"model\":\"vision-model\""))
            assertTrue(capturedBody.contains("data:image/png;base64,AQID"))
            assertTrue(capturedBody.contains("\"instructions\":\"You are a precise OCR engine for UI screenshots.\""))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `complete supports streaming chat response`() {
        var capturedBody = ""
        val events = mutableListOf<OpenAiCompatibleStreamEvent>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/responses") { exchange ->
            capturedBody = exchange.requestBody.bufferedReader().use { it.readText() }
            exchange.respondStream(
                listOf(
                    """data: {"type":"response.reasoning_summary_text.delta","item_id":"rs_1","output_index":0,"sequence_number":1,"summary_index":0,"delta":"reading screenshot"}""",
                    """data: {"type":"response.output_text.delta","content_index":0,"delta":"{\"texts\":","item_id":"msg_1","logprobs":[],"output_index":0,"sequence_number":2}""",
                    """data: {"type":"response.output_text.delta","content_index":0,"delta":"[{\"text\":\"OK\",\"bounds\":{\"x\":0,\"y\":0,\"width\":1,\"height\":1}}]}","item_id":"msg_1","logprobs":[],"output_index":0,"sequence_number":3}""",
                    """data: [DONE]"""
                )
            )
        }
        server.start()

        try {
            val client = OpenAiCompatibleMultimodalOcrClient(
                baseUrl = URI.create("http://127.0.0.1:${server.address.port}"),
                model = "vision-model",
                apiKey = "token",
                timeout = Duration.ofSeconds(2),
                stream = true,
                onStreamEvent = events::add
            )

            val content = client.complete(
                MultimodalOcrRequest(
                    imageBytes = byteArrayOf(1, 2, 3),
                    width = 2,
                    height = 2,
                    mimeType = "image/png",
                    prompt = "Read text"
                )
            )

            assertEquals(
                """{"texts":[{"text":"OK","bounds":{"x":0,"y":0,"width":1,"height":1}}]}""",
                content
            )
            assertTrue(capturedBody.contains("\"stream\":true"))
            assertEquals(OpenAiCompatibleStreamEvent.Reasoning("reading screenshot"), events.first())
            assertTrue(events.filterIsInstance<OpenAiCompatibleStreamEvent.Content>().isNotEmpty())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `fromConfig lets caller provide base url api key and model`() {
        var requestedPath = ""
        var capturedBody = ""
        var capturedAuthorization: String? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/responses") { exchange ->
            requestedPath = exchange.requestURI.path
            capturedBody = exchange.requestBody.bufferedReader().use { it.readText() }
            capturedAuthorization = exchange.requestHeaders.getFirst("Authorization")
            exchange.respond(responseJson("""{"texts":[]}"""))
        }
        server.start()

        try {
            val client = OpenAiCompatibleMultimodalOcrClient.fromConfig(
                OpenAiCompatibleMultimodalOcrConfig(
                    baseUrl = URI.create("http://127.0.0.1:${server.address.port}/v1"),
                    apiKey = "caller-key",
                    model = "caller-model",
                    reasoningEffort = "high",
                    stream = false
                )
            )

            val content = client.complete(
                MultimodalOcrRequest(
                    imageBytes = byteArrayOf(1, 2, 3),
                    width = 2,
                    height = 2,
                    mimeType = "image/png",
                    prompt = "Read text"
                )
            )

            assertEquals("""{"texts":[]}""", content)
            assertEquals("/v1/responses", requestedPath)
            assertEquals("Bearer caller-key", capturedAuthorization)
            assertTrue(capturedBody.contains("\"model\":\"caller-model\""))
            assertTrue(capturedBody.contains("\"reasoning\":{\"effort\":\"high\""))
        } finally {
            server.stop(0)
        }
    }

    private fun responseJson(outputText: String): String {
        val escaped = outputText
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return """
            {
              "id": "resp_test",
              "object": "response",
              "created_at": 0,
              "model": "vision-model",
              "output": [
                {
                  "id": "msg_1",
                  "type": "message",
                  "role": "assistant",
                  "status": "completed",
                  "content": [
                    {
                      "type": "output_text",
                      "text": "$escaped",
                      "annotations": []
                    }
                  ]
                }
              ],
              "parallel_tool_calls": true,
              "tool_choice": "auto",
              "tools": []
            }
        """.trimIndent()
    }

    private fun HttpExchange.respond(body: String) {
        val bytes = body.toByteArray()
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(200, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun HttpExchange.respondStream(lines: List<String>) {
        responseHeaders.set("Content-Type", "text/event-stream")
        sendResponseHeaders(200, 0)
        responseBody.bufferedWriter().use { writer ->
            lines.forEach { line ->
                writer.write(line)
                writer.write("\n\n")
                writer.flush()
            }
        }
    }
}
