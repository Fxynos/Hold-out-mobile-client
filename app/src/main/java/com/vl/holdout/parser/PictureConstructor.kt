package com.vl.holdout.parser

import android.graphics.*
import androidx.core.text.trimmedLength
import com.caverock.androidsvg.SVG
import com.vl.holdout.parser.pojo.Picture
import java.io.File
import java.util.function.Consumer
import kotlin.streams.toList

internal class PictureConstructor(
    private val pictureRepository: Repository<Picture>,
    private val filesSource: File
): Constructor<Picture>("picture") {

    override fun create(properties: Map<String, String>) = Picture(
        properties.require(type),
        properties.require("x").toDoubleOrNull() ?: throw ParseException("$type \"${properties.require(type)}\" \"x\" property is decimal number"),
        properties.require("y").toDoubleOrNull() ?: throw ParseException("$type \"${properties.require(type)}\" \"y\" property is decimal number"),
        properties.require("width").toDoubleOrNull() ?: throw ParseException("$type \"${properties.require(type)}\" \"width\" property is decimal number"),
        properties.require("height").toDoubleOrNull() ?: throw ParseException("$type \"${properties.require(type)}\" \"height\" property is decimal number")
    )

    /**
     * Blocking method (loads bitmaps)
     */
    override fun finish(instance: Picture, properties: Map<String, String>) {
        instance.layers = properties.require("layers").split(DELIMITER_ARRAY).stream()
            .map { sPair -> sPair.split(DELIMITER_PAIR, limit = 2).let { it[0].trim().lowercase() to if (it.size > 1) it[1].trim() else throw ParseException("$type \"${instance.id}\": $sPair") } }
            .map {
                when (it.first) {
                    "color" -> ColorDrawer(instance.x, instance.y, instance.width, instance.height, it.second)
                    "file" -> FileDrawer(instance.x, instance.y, instance.width, instance.height, File(filesSource, it.second))
                    "picture" -> PictureDrawer(instance.x, instance.y, instance.width, instance.height, pictureRepository[it.second])
                    else -> throw ParseException("$type \"${instance.id}\" unknown layer type: ${properties.require("layers")}")
                }
            }.toList().toTypedArray()
    }
}

private abstract class Drawer(
    open val x: Double,
    open val y: Double,
    open val width: Double,
    open val height: Double
): Consumer<Canvas> {
    protected fun (Canvas).viewPort() = RectF(
        (x * this.width).toFloat(),
        (y * this.height).toFloat(),
        ((1 - x - width) * this.width).toFloat(),
        ((1 - y - height) * this.height).toFloat()
    )
}

private class ColorDrawer(
    override val x: Double,
    override val y: Double,
    override val width: Double,
    override val height: Double,
    val color: String
): Drawer(x, y, width, height) {
    val paint = Paint()
    init {
        paint.color = Color.parseColor("#$color".uppercase())
        paint.style = Paint.Style.FILL
    }

    override fun accept(canvas: Canvas) =
        canvas.drawRect(
            (x * canvas.width).toFloat(),
            (y * canvas.height).toFloat(),
            ((1 - x - width) * canvas.width).toFloat(),
            ((1 - y - height) * canvas.height).toFloat(),
            paint
        )
}

private class FileDrawer(
    override val x: Double,
    override val y: Double,
    override val width: Double,
    override val height: Double,
    val file: File
): Drawer(x, y, width, height) {

    init {
        if (!file.exists())
            throw ParseException("Couldn't load file for picture: ${file.absolutePath}")
    }
    override fun accept(canvas: Canvas) =
        if (file.name.endsWith(".svg"))
            drawVector(canvas)
        else
            drawBitmap(canvas)

    private fun drawBitmap(canvas: Canvas) =
        canvas.drawBitmap(
            BitmapFactory.decodeFile(file.absolutePath), // TODO avoid blocking in runtime
            Rect(0, 0, 0, 0),
            canvas.viewPort(),
            null
        )

    private fun drawVector(canvas: Canvas) =
        SVG.getFromInputStream(file.inputStream()).renderToCanvas(
            canvas,
            canvas.viewPort()
        )
}

private class PictureDrawer(
    override val x: Double, // has no effect yet
    override val y: Double,
    override val width: Double,
    override val height: Double,
    val picture: Picture
): Drawer(x, y, width, height) {
    override fun accept(canvas: Canvas) = picture.layers.forEach { it.accept(canvas) } // TODO nested picture doesn't use passed x, y, w, h yet
}