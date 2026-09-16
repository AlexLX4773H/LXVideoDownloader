package com.example.lxvideodownloader

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data class WebBrowser(val url: String) : NavKey
