package com.kai.custom.data.dimension

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dimensionModule = module {
    single<DimensionStore> {
        SqliteDimensionStore(androidContext()).also { it.initialize() }
    }
}
