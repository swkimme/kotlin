deprecated("Object")
object Obsolete {
    fun use() {}
}

class Another {
    deprecated("Object")
    companion object Obsolete {
        fun use() {}
    }
}

fun useObject() {
    Obsolete.use()
    val x = Obsolete
}

fun useCompanion() {
    val d = Another
    val x = Another.Companion
}