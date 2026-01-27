package com.spendwise.core.com.spendwise.core.ml

object SenderNormalizer {

    fun normalize(sender: String): String {
        return sender
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "") // remove AX-, VK-, etc.
    }
}



