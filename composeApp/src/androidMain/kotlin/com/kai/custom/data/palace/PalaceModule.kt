package com.kai.custom.data.palace

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val palaceModule = module {
    single<PalaceStore> {
        SqlitePalaceStore(androidContext()).also { it.initialize() }
    }
    single<PalaceBackupManager> {
        AndroidPalaceBackup(androidContext())
    }
}
