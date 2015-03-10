inline fun a() { b() }
inline fun b() { a() }

inline fun x() { y() }
inline fun y() { z() }
inline fun z() { x() }

public fun main(args: Array<String>): Unit {}