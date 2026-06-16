package com.kai.custom.data.dimension

import org.koin.dsl.module
import java.io.File

val dimensionModule = module {
    single<DimensionStore> {
        val userHome = System.getProperty("user.home")
        val kaiDir = File("$userHome/.kai")
        if (!kaiDir.exists()) kaiDir.mkdirs()
        val dbPath = File(kaiDir, "kai_dimension.db").absolutePath
        JdbcDimensionStore(dbPath).also { it.initialize() }
    }
}
