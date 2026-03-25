#if (${PACKAGE_NAME} && ${PACKAGE_NAME} != "")package ${PACKAGE_NAME}.${Name.toLowerCase()} #end

#parse("File Header.java")
import androidx.compose.runtime.Composable

@Composable
fun ${Name}Screen(
    uiState: ${Name}UiState,
) {
}
