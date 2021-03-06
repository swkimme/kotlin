// !DIAGNOSTICS: -UNUSED_EXPRESSION -UNUSED_PARAMETER -UNUSED_VARIABLE -NOTHING_TO_INLINE -ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE -UNUSED_VALUE
inline fun <T, U> Function1<T, U>.component1() = 1
inline fun <T, U> Function1<T, U>.component2() = 2

inline fun <T, U, V> ExtensionFunction1<T, U, V>.component1() = 1
inline fun <T, U, V> ExtensionFunction1<T, U, V>.component2() = 2

inline fun <T, U, V> inlineFunWithInvoke(s: (p: T) -> U, ext: T.(p: U) -> V) {
    val (d1, e1) = s
    val (d2, e2) = ext
}

fun <T, U, V> Function2<T, U, V>.component1() = 1
fun <T, U, V> Function2<T, U, V>.component2() = 2

fun <T, U, V, W> ExtensionFunction2<T, U, V, W>.component1() = 1
fun <T, U, V, W> ExtensionFunction2<T, U, V, W>.component2() = 2

inline fun <T, U, V, W> inlineFunWithInvoke(s: (p: T, l: U) -> V, ext: T.(p: U, l: V) -> W) {
    val (d1, e1) = <!USAGE_IS_NOT_INLINABLE, USAGE_IS_NOT_INLINABLE!>s<!>
    val (d2, e2) = <!USAGE_IS_NOT_INLINABLE, USAGE_IS_NOT_INLINABLE!>ext<!>
}