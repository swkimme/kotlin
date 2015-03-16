class UsefulClass(val param: Int = 2) {
    fun get(instance: Any, property: PropertyMetadata) : Int = 1
    fun set(instance: Any, property: PropertyMetadata, value: Int) {}
}

deprecated("message")
fun Obsolete(param: Int = 1): UsefulClass = UsefulClass(param)

fun block() {
    <!DEPRECATED_SYMBOL_WITH_MESSAGE!>Obsolete<!>()
    <!DEPRECATED_SYMBOL_WITH_MESSAGE!>Obsolete<!>(2)
}

fun expression() = <!DEPRECATED_SYMBOL_WITH_MESSAGE!>Obsolete<!>()

fun reflection() = <!REFLECTION_TYPES_NOT_LOADED!>::<!><!DEPRECATED_SYMBOL_WITH_MESSAGE!>Obsolete<!>

class Initializer {
    val x = <!DEPRECATED_SYMBOL_WITH_MESSAGE!>Obsolete<!>()
}

class Delegation {
    val x by <!DEPRECATED_SYMBOL_WITH_MESSAGE!>Obsolete<!>()
    var y by <!DEPRECATED_SYMBOL_WITH_MESSAGE!>Obsolete<!>()
}