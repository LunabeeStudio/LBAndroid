#if (${PACKAGE_NAME} && ${PACKAGE_NAME} != "")package ${PACKAGE_NAME}.${Name.toLowerCase()} #end

#parse("File Header.java")

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
data object ${Name}Destination {
    fun composable(navGraphBuilder: NavGraphBuilder, navScope: ${Name}NavScope) {
        navGraphBuilder.composable<${Name}Destination> {
            val presenter: ${Name}Presenter = koinViewModel()
            presenter.invoke(navScope)
        }
    }
}
