package com.spendwise.domain.com.spendwise.feature.smsimport.data

object SmsTemplateUtil {

    // masked accounts like XX674, XXXX6111, *1601
    private val maskedAccount = Regex("\\b[xX*]{2,}\\d+\\b")

    // credited names / entities (ALL CAPS words)
    private val creditedName = Regex(
        "(?<=;|to|sent to|credited\\s)([A-Z][A-Z ]{2,40})",
        RegexOption.IGNORE_CASE
    )

    private val numberLike = Regex("\\d{2,}")
    private val dateLike = Regex("\\b\\d{1,2}[/-][a-zA-Z0-9]{1,3}[/-]\\d{2,4}\\b")

    fun buildTemplate(body: String): String {
        var text = body.lowercase()

        // 1️⃣ Preserve masked accounts
        val accounts = maskedAccount.findAll(text).map { it.value }.toList()
        accounts.forEachIndexed { idx, acc ->
            text = text.replace(acc, "__ACC_${idx}__")
        }

        // 2️⃣ Preserve credited names / entities
        val names = creditedName.findAll(text).map { it.value.trim() }.toList()
        names.forEachIndexed { idx, name ->
            text = text.replace(name, "__NAME_${idx}__")
        }

        // 3️⃣ Replace variable parts
        text = text
            .replace(dateLike, "<VAR>")
            .replace(numberLike, "<VAR>")
            .replace(Regex("\\s+"), " ")
            .trim()

        // 4️⃣ Restore anchors
        accounts.forEachIndexed { idx, acc ->
            text = text.replace("__ACC_${idx}__", acc)
        }
        names.forEachIndexed { idx, name ->
            text = text.replace("__NAME_${idx}__", name)
        }

        text = text
            .replace(Regex("[^a-z<> ]"), " ")   // strip punctuation
            .replace(Regex("\\s+"), " ")
            .trim()

        return text
    }
}

