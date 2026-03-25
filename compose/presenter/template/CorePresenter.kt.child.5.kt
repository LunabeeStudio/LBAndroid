#if (${PACKAGE_NAME} && ${PACKAGE_NAME} != "")package ${PACKAGE_NAME}.${Name.toLowerCase()} #end

#parse("File Header.java")
import androidx.compose.runtime.Stable
import studio.lunabee.compose.presenter.PresenterUiState

@Stable
data class ${Name}UiState(

) : PresenterUiState
