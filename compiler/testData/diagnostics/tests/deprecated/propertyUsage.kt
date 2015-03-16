class Delegate() {
    deprecated("text")
    fun get(instance: Any, property: PropertyMetadata) : Int = 1

    deprecated("text")
    fun set(instance: Any, property: PropertyMetadata, value: Int) {}
}

class PropertyHolder {
    deprecated("text")
    val x = 1

    deprecated("text")
    var name = "String"

    val valDelegate <!DEPRECATED_SYMBOL_WITH_MESSAGE!>by<!> Delegate()
    var varDelegate <!DEPRECATED_SYMBOL_WITH_MESSAGE, DEPRECATED_SYMBOL_WITH_MESSAGE!>by<!> Delegate()
}

fun fn() {
    val <!UNUSED_VARIABLE!>a<!> = PropertyHolder().<!DEPRECATED_SYMBOL_WITH_MESSAGE!>x<!>
    val <!UNUSED_VARIABLE!>b<!> = PropertyHolder().<!DEPRECATED_SYMBOL_WITH_MESSAGE!>name<!>
    PropertyHolder().<!DEPRECATED_SYMBOL_WITH_MESSAGE!>name<!> = "value"

    val <!UNUSED_VARIABLE!>d<!> = PropertyHolder().valDelegate
    PropertyHolder().varDelegate = 1
}