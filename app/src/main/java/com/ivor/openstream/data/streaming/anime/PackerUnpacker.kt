package com.ivor.openstream.data.streaming.anime

/**
 * Expands Dean Edwards' p.a.c.k.e.r scripts:
 * `eval(function(p,a,c,k,e,d){...}('...',a,c,'...'.split('|')))`. Returns the input unchanged
 * when it is not packed.
 */
internal object PackerUnpacker {
    private val PACKED = Regex(
        """\}\('(.*)',\s*(\d+),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""",
        RegexOption.DOT_MATCHES_ALL
    )
    private val WORD = Regex("""\b\w+\b""")

    fun unpack(js: String): String {
        val match = PACKED.find(js) ?: return js
        val (payload, radixRaw, countRaw, wordsRaw) = match.destructured
        val radix = radixRaw.toInt()
        val count = countRaw.toInt()
        val words = wordsRaw.split('|')

        fun encode(n: Int): String {
            val prefix = if (n < radix) "" else encode(n / radix)
            val digit = n % radix
            val char = if (digit > 35) (digit + 29).toChar().toString() else digit.toString(36)
            return prefix + char
        }

        val dictionary = HashMap<String, String>(count)
        for (i in 0 until count) {
            val key = encode(i)
            dictionary[key] = words.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: key
        }
        return WORD.replace(payload.replace("\\'", "'")) { dictionary[it.value] ?: it.value }
    }
}
