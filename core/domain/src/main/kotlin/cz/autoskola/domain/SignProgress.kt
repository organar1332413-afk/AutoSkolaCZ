package cz.autoskola.domain

/** Independent states: viewing a sign never changes its bookmark. Codes are stable legal IDs. */
data class SignProgress(val viewed: Set<String> = emptySet(), val favorites: Set<String> = emptySet())
