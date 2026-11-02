package com.ivor.openstream.data.extensions

/**
 * A repository problem whose message is already localized and fit to show as is. Anything else a
 * repository fetch throws is mapped to a plain message instead of showing its own text.
 */
class RepoException(message: String) : Exception(message)
