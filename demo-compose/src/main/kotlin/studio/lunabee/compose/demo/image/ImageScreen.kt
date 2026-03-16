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

package studio.lunabee.compose.demo.image

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import studio.lunabee.compose.R
import studio.lunabee.compose.core.LbcImageSpec
import studio.lunabee.compose.image.LbcImage

@Composable
fun ImageScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 16.dp),
    ) {
        Text("No force")
        LbcImage(LbcImageSpec.ImageDrawable(R.drawable.sun_moon))
        Text("UI_MODE_NIGHT_NO")
        LbcImage(LbcImageSpec.ImageDrawable(R.drawable.sun_moon, Configuration.UI_MODE_NIGHT_NO))
        Text("UI_MODE_NIGHT_YES")
        LbcImage(LbcImageSpec.ImageDrawable(R.drawable.sun_moon, Configuration.UI_MODE_NIGHT_YES))
        Text("Fallback URL")
        LbcImage(
            LbcImageSpec.Url(
                url = "https://picsum.photos/200/300",
                allowCaching = false,
                fallback = LbcImageSpec.ImageDrawable(R.drawable.oss117),
            ),
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ImageScreenPreview() {
    Surface(Modifier.fillMaxWidth()) {
        ImageScreen()
    }
}
