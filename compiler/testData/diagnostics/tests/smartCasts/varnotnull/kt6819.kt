public fun test(o: String?): Boolean {
    return when {
        o == null, <!DEBUG_INFO_SMARTCAST!>o<!>.length() == 0 -> false // 'o' is assumed to be 'String?', and error is reported
        else -> true
    }
}