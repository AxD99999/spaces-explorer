package com.hfspaces.explorer.data.model

enum class StatusFilter(val label: String) {
    ALL("All"),
    RUNNING("Running"),
    SLEEPING("Sleeping")
}

/** [serverKey] is the Hub's `sort` value; null means the order is applied locally (or is the default). */
enum class SortOption(val displayName: String, val serverKey: String?) {
    TRENDING_DESC("Trending", null),
    LIKES_DESC("Most likes", "likes"),
    RECENT_DESC("Recently updated", "modified"),
    CREATED_DESC("Newest", "created"),
    NAME_ASC("Name (A–Z)", null),
    NAME_DESC("Name (Z–A)", null)
}

/**
 * Browse filters. [search], [category], [sdk] and server-backed sorts are sent to the Hub;
 * the rest is applied locally to the pages loaded so far (see [applyLocalFilters]).
 */
data class SpaceFilterState(
    val search: String = "",
    val category: String = ALL,
    val status: StatusFilter = StatusFilter.ALL,
    val sdk: String = ALL,
    val hardware: String = ALL,
    val pinnedOnly: Boolean = false,
    val featuredOnly: Boolean = false,
    val sortBy: SortOption = SortOption.TRENDING_DESC
) {
    /** Count of options set in the filter sheet (search and category are visible elsewhere). */
    val sheetFilterCount: Int
        get() = listOf(
            status != StatusFilter.ALL,
            sdk != ALL,
            hardware != ALL,
            pinnedOnly,
            featuredOnly,
            sortBy != SortOption.TRENDING_DESC
        ).count { it }

    val isDefault: Boolean
        get() = this == SpaceFilterState()

    /** True when the remote query differs, i.e. the loaded pages must be thrown away. */
    fun remoteQueryDiffers(other: SpaceFilterState): Boolean =
        search.trim() != other.search.trim() ||
            category != other.category ||
            sdk != other.sdk ||
            sortBy.serverKey != other.sortBy.serverKey
}

fun List<HfSpace>.applyLocalFilters(filter: SpaceFilterState): List<HfSpace> {
    val filtered = filter { space ->
        when (filter.status) {
            StatusFilter.ALL -> true
            StatusFilter.RUNNING -> space.stage == SpaceStage.RUNNING
            StatusFilter.SLEEPING -> space.stage == SpaceStage.SLEEPING
        } &&
            (filter.sdk == ALL || space.sdk.equals(filter.sdk, ignoreCase = true)) &&
            (filter.hardware == ALL || space.hardwareLabel == filter.hardware) &&
            (!filter.pinnedOnly || space.pinned) &&
            (!filter.featuredOnly || space.featured)
    }
    // Sorts are stable, so equal keys keep the Hub's (trending) order.
    return when (filter.sortBy) {
        SortOption.TRENDING_DESC -> filtered
        SortOption.LIKES_DESC -> filtered.sortedByDescending { it.likes }
        SortOption.RECENT_DESC -> filtered.sortedByDescending { it.lastModified.orEmpty() }
        SortOption.CREATED_DESC -> filtered.sortedByDescending { it.createdAt.orEmpty() }
        SortOption.NAME_ASC -> filtered.sortedBy { it.displayTitle.lowercase() }
        SortOption.NAME_DESC -> filtered.sortedByDescending { it.displayTitle.lowercase() }
    }
}

/** Appends [next] to the list, dropping Spaces already present (pages can overlap as rankings shift). */
fun List<HfSpace>.appendDistinct(next: List<HfSpace>): List<HfSpace> {
    val seen = mapTo(HashSet()) { it.id }
    return this + next.filter { seen.add(it.id) }
}
