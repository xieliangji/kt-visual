package com.soluna.ktvisual.api

import com.soluna.ktvisual.model.OcrText
import com.soluna.ktvisual.model.Region
import java.awt.image.BufferedImage

interface OcrEngine {
    fun recognize(image: BufferedImage, roi: Region? = null): List<OcrText>
}
