package com.soluna.ktvisual.api

class VisionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)