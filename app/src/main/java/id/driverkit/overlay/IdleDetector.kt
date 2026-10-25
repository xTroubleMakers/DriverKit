package id.driverkit.overlay

/** Deteksi "diam": kecepatan <= 3 km/jam lebih lama dari [Settings.idleWarnMin]. */
class IdleDetector {

    private var stillSince = 0L
    private var warned = false

    /** true = saatnya memberi peringatan idle (sekali per periode diam). */
    fun shouldWarn(speed: Float): Boolean {
        val limit = Settings.idleWarnMin
        if (limit <= 0) return false
        val now = System.currentTimeMillis()

        if (speed <= 3f) {
            if (stillSince == 0L) stillSince = now
            val idleMs = now - stillSince
            if (!warned && idleMs >= limit * 60_000L) {
                warned = true
                return true
            }
        } else {
            stillSince = 0L
            warned = false
        }
        return false
    }

    fun reset() {
        stillSince = 0L
        warned = false
    }
}
