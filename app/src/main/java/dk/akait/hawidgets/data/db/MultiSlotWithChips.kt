package dk.akait.hawidgets.data.db

/**
 * Én slot og dens sekundær-chips samlet — bruges hvor de to logisk hører sammen (config-UI'ets
 * in-memory tilstand, eksport/import, log-dump). Persisteres til to separate tabeller
 * (multi_widget_slot / multi_widget_chip); denne wrapper er kun en transport-bekvemmelighed,
 * ingen Room-relation.
 */
data class MultiSlotWithChips(
    val slot: MultiWidgetSlotEntity,
    val chips: List<MultiWidgetChipEntity>,
)
