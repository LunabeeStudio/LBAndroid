#if (${PACKAGE_NAME} && ${PACKAGE_NAME} != "")package ${PACKAGE_NAME}.${Name.toLowerCase()} #end

#parse("File Header.java")

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.Flow
import studio.lunabee.compose.presenter.LBPresenterContext
import studio.lunabee.compose.presenter.LBSinglePresenter

class ${Name}Presenter(
    savedStateHandle: SavedStateHandle,
) : LBSinglePresenter<${Name}UiState, ${Name}NavScope, ${Name}Action>() {

    private val params: ${Name}Destination = savedStateHandle.toRoute()

    override val flows: List<Flow<${Name}Action>> = listOf()
    
    override fun createReducer(runtime: LBPresenterContext<${Name}Action>): ${Name}Reducer {
        return ${Name}Reducer(
            coroutineScope = runtime.coroutineScope,
            emitUserAction = runtime.emitUserAction,
        )
    }
    

    override fun getInitialState(): ${Name}UiState = ${Name}UiState()

    override val content: @Composable (${Name}UiState) -> Unit = { ${Name}Screen(it) }
}
