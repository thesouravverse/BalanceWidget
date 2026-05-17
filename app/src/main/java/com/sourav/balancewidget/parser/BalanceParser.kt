package com.sourav.balancewidget.parser

/**
 * Parses bank SMS / notification text into a transaction:
 *   - amount + direction (debit/credit)
 *   - account suffix (last 4 digits) so we can filter to ONE tracked account
 *   - optional balance from bank (used as ground-truth when present)
 *
 * The Repository decides whether to apply the txn based on user-configured account suffix.
 */
object BalanceParser {

    private val bankHints = listOf(
        "HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "YES BANK", "IDFC",
        "PNB", "BOB", "CANARA", "INDIAN BANK", "UNION BANK", "FEDERAL"
    )

    /**
     * Captures the last 4-6 digits of an account/card from common HDFC formats:
     *   "A/C *9504", "a/c XX1234", "card ending 0974", "card xx0974", "A/c **9504"
     */
    private val accountSuffixRegex = Regex(
        """(?:a/c|account|card)[\s.:]*(?:\*{1,2}|x{2,}|ending\s+)?\s*(\d{4,6})""",
        RegexOption.IGNORE_CASE
    )

    private val debitAmountRegex = Regex(
        """(?:sent|debited|spent|withdrawn|paid|purchase\s+of|txn\s+of|payment\s+of)\s+(?:rs\.?|inr|₹)?\s*([0-9]{1,3}(?:,?[0-9]{2,3})*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val creditAmountRegex = Regex(
        """(?:received|credited|deposited|refund(?:ed)?|added)\s+(?:rs\.?|inr|₹)?\s*([0-9]{1,3}(?:,?[0-9]{2,3})*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val amountThenDirectionRegex = Regex(
        """(?:rs\.?|inr|₹)\s*([0-9]{1,3}(?:,?[0-9]{2,3})*(?:\.[0-9]{1,2})?)\s*(debited|credited|spent|sent|received|withdrawn|deposited)""",
        RegexOption.IGNORE_CASE
    )

    private val balanceRegex = Regex(
        """(?:avl[\s.]*bal(?:ance)?|available[\s.]*bal(?:ance)?)\s*[:\-]?\s*(?:rs\.?|inr|₹)?\s*([0-9]{1,3}(?:,?[0-9]{2,3})*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    enum class Direction { DEBIT, CREDIT, UNKNOWN }

    data class ParsedTxn(
        val amount: Double,
        val direction: Direction,
        val accountSuffix: String?,
        val balanceFromBank: Double? = null,
        val rawText: String,
        val source: String,
        val sender: String? = null
    )

    fun looksLikeBankMessage(text: String, sender: String? = null): Boolean {
        val haystack = (text + " " + (sender ?: "")).uppercase()
        if (bankHints.any { haystack.contains(it) }) return true
        return haystack.contains("A/C") || haystack.contains("AVL BAL") || haystack.contains("CARD ENDING")
    }

    fun parseTxn(
        text: String,
        source: String,
        sender: String? = null
    ): ParsedTxn? {
        if (!looksLikeBankMessage(text, sender)) return null

        var amount: Double? = null
        var direction: Direction = Direction.UNKNOWN

        debitAmountRegex.find(text)?.let {
            amount = it.groupValues[1].replace(",", "").toDoubleOrNull()
            direction = Direction.DEBIT
        }
        if (amount == null) {
            creditAmountRegex.find(text)?.let {
                amount = it.groupValues[1].replace(",", "").toDoubleOrNull()
                direction = Direction.CREDIT
            }
        }
        if (amount == null) {
            amountThenDirectionRegex.find(text)?.let {
                amount = it.groupValues[1].replace(",", "").toDoubleOrNull()
                direction = when (it.groupValues[2].lowercase()) {
                    "debited", "spent", "sent", "withdrawn" -> Direction.DEBIT
                    "credited", "received", "deposited" -> Direction.CREDIT
                    else -> Direction.UNKNOWN
                }
            }
        }
        val finalAmount = amount ?: return null
        if (direction == Direction.UNKNOWN) return null

        val suffix = accountSuffixRegex.find(text)?.groupValues?.get(1)

        val balFromBank = balanceRegex.find(text)
            ?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toDoubleOrNull()

        return ParsedTxn(
            amount = finalAmount,
            direction = direction,
            accountSuffix = suffix,
            balanceFromBank = balFromBank,
            rawText = text,
            source = source,
            sender = sender
        )
    }
}
