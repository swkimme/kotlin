package foo

// NOTE THIS FILE IS AUTO-GENERATED by the generateTestDataForReservedWords.kt. DO NOT EDIT!

object TestObject {
    val import: Int = 0

    fun test() {
        testNotRenamed("import", { import })
    }
}

fun box(): String {
    TestObject.test()

    return "OK"
}