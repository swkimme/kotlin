// "Create class 'Foo'" "false"
// ACTION: Create function 'Foo'
// ACTION: Remove parameter 's'
// ACTION: Split property declaration
// ACTION: Create secondary constructor
// ERROR: No value passed for parameter s

class Foo(i: Int, s: String)

fun test() {
    val a = Foo(2<caret>)
}