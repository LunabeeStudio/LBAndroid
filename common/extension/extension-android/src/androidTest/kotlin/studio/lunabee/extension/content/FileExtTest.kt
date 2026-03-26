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

package studio.lunabee.extension.content

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import studio.lunabee.extension.BitmapUtils
import studio.lunabee.extension.downscaleAndCrop
import studio.lunabee.extension.downscaleAndCropPreR
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileExtTest {

    companion object {
        private val context = ApplicationProvider.getApplicationContext<Context>()
        private val srcResize = File(context.cacheDir, "src_resize")
        private val srcAlpha = File(context.cacheDir, "src_alpha")

        @BeforeClass
        @JvmStatic
        fun setup() {
            srcResize.writeBytes(this::class.java.classLoader!!.getResourceAsStream("src_resize_test_259_194.jpeg").readBytes())
            srcAlpha.writeBytes(this::class.java.classLoader!!.getResourceAsStream("src_png_transparent_320_213.png").readBytes())
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            context.cacheDir.deleteRecursively()
        }
    }

    @Test
    fun downscaleAndCrop_compress_jpeg() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val expectedData = this.javaClass.classLoader!!.getResourceAsStream("target_resize_test_100_100.jpeg").readBytes()
        val expectedBitmap = BitmapFactory.decodeByteArray(expectedData, 0, expectedData.size)

        val target = File(context.cacheDir, "target_downscaleAndCrop_compress_jpeg.jpeg")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            srcResize.downscaleAndCrop(target, 100, 100, Bitmap.CompressFormat.JPEG)
        } else {
            srcResize.downscaleAndCropPreR(target, 100, 100, Bitmap.CompressFormat.JPEG)
        }

        val actualBitmap = BitmapFactory.decodeFile(target.absolutePath)
        val diff = BitmapUtils.getDifferencePercent(expectedBitmap, actualBitmap)

        assertEquals(100, actualBitmap.height)
        assertEquals(100, actualBitmap.width)

        assertTrue(diff < 1)
    }

    @Test
    fun downscaleAndCrop_ratio() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val expectedData = this.javaClass.classLoader!!.getResourceAsStream("target_resize_test_from_6000_3000.webp").readBytes()
        val expectedBitmap = BitmapFactory.decodeByteArray(expectedData, 0, expectedData.size)

        val target = File(context.cacheDir, "target_downscaleAndCrop_ratio.webp")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            srcResize.downscaleAndCrop(target, 6000, 3000)
        } else {
            srcResize.downscaleAndCropPreR(target, 6000, 3000)
        }

        val actualBitmap = BitmapFactory.decodeFile(target.absolutePath)

        val diff = BitmapUtils.getDifferencePercent(expectedBitmap, actualBitmap)

        assertEquals(129, actualBitmap.height)
        assertEquals(259, actualBitmap.width)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertTrue(diff < 0.1)
        } else {
            assertTrue(diff < 0.5)
        }
    }

    @Test
    fun downscaleAndCrop_downscale() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val expectedData = this.javaClass.classLoader!!.getResourceAsStream("target_resize_test_100_100.webp").readBytes()
        val expectedBitmap = BitmapFactory.decodeByteArray(expectedData, 0, expectedData.size)

        val target = File(context.cacheDir, "target_downscaleAndCrop_downscale.webp")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            srcResize.downscaleAndCrop(target, 100, 100)
        } else {
            srcResize.downscaleAndCropPreR(target, 100, 100)
        }

        val actualBitmap = BitmapFactory.decodeFile(target.absolutePath)
        val diff = BitmapUtils.getDifferencePercent(expectedBitmap, actualBitmap)

        assertEquals(100, actualBitmap.height)
        assertEquals(100, actualBitmap.width)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertTrue(diff < 0.1)
        } else {
            assertTrue(diff < 1.7)
        }
    }

    @Test
    fun downscaleAndCrop_keep_alpha() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val expectedData = this.javaClass.classLoader!!.getResourceAsStream("src_png_transparent_320_213.png").readBytes()
        val expectedBitmap = BitmapFactory.decodeByteArray(expectedData, 0, expectedData.size)

        val target = File(context.cacheDir, "target_downscaleAndCrop_keep_alpha.webp")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            srcAlpha.downscaleAndCrop(target, 320, 213)
        } else {
            srcAlpha.downscaleAndCropPreR(target, 320, 213)
        }

        val actualBitmap = BitmapFactory.decodeFile(target.absolutePath)
        val diff = BitmapUtils.getDifferencePercent(expectedBitmap, actualBitmap)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertTrue(diff < 0.1)
        } else {
            assertTrue(diff < 0.5)
        }
    }
}
