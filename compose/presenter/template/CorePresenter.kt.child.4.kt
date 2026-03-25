#if (${PACKAGE_NAME} && ${PACKAGE_NAME} != "")package ${PACKAGE_NAME}.${Name.toLowerCase()} #end

#parse("File Header.java")
import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import studio.lunabee.compose.presenter.LBSingleReducer
import studio.lunabee.compose.presenter.ReduceResult
import studio.lunabee.compose.presenter.asResult

class ${Name}Reducer(
    override val coroutineScope: CoroutineScope,
    override val emitUserAction: (${Name}Action) -> Unit,
) : LBSingleReducer<${Name}UiState, ${Name}NavScope, ${Name}Action>() {
    override suspend fun reduce(
        actualState: ${Name}UiState,
        action: ${Name}Action,
        performNavigation: (${Name}NavScope.() -> Unit) -> Unit,
        useActivity: (suspend (Activity) -> Unit) -> Unit,
    ): ReduceResult<${Name}UiState> {
        return when(action) {
        }
    }
}
