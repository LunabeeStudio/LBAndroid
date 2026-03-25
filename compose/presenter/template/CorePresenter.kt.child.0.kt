#if (${PACKAGE_NAME} && ${PACKAGE_NAME} != "")package ${PACKAGE_NAME}.${Name.toLowerCase()} #end

#parse("File Header.java")

sealed interface ${Name}Action
