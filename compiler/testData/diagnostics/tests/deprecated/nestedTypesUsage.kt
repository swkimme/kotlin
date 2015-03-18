class TopLevel {
    deprecated("Nested")
    class Nested {
        companion object {
            fun use() {}
        }
    }
}

fun useNested() {
    val <!UNUSED_VARIABLE!>d<!> = TopLevel.<!DEPRECATED_SYMBOL_WITH_MESSAGE!>Nested<!>.use()
}