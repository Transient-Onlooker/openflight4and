package com.example.openflight4and.ui

import androidx.compose.runtime.compositionLocalOf
import com.example.openflight4and.data.AppRepository

val LocalAppRepository = compositionLocalOf<AppRepository> {
    error("AppRepository was not provided")
}
