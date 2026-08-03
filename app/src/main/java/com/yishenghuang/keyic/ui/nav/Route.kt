package com.yishenghuang.keyic.ui.nav

sealed class Route(val path: String) {
    data object Setup : Route("setup")
    data object Lock : Route("lock")
    data object Home : Route("home")
    data object Vault : Route("vault")
    data object Generator : Route("generator")
    data object Health : Route("health")
    data object Settings : Route("settings")
    data object EntryDetail : Route("entry/{id}") {
        fun create(id: String) = "entry/$id"
    }
    data object EntryEdit : Route("edit/{id}") {
        fun create(id: String? = null) = "edit/${id ?: "new"}"
    }
}
