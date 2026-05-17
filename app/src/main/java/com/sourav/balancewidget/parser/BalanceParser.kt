package com.sourav.balancewidget.parser

/**
 * Parses bank SMS / notification text and extracts:
 *   - balance (Avl Bal / Available balance)
 *   - txn amount + direction (debited/credited)
 *
 * Tuned for HDFC first; structure is bank-agnostic so we can add SBI, ICICI, etc.
 *
 * Sample HDFC formats seen in the wild:
 *   "Sent Rs.500.00 From HDFC Bank A/C *1234 To X On 15/05/26. Avl Bal:Rs.12,345.67"
 *   "Update! INR 500.00 deposited in HDFC Bank A/c XX1234 ... Avl bal INR 12,345.67"
 *   "Rs.500.00 debited from a/c XX1234 ... Avl Bal: Rs. 12,345.67"
 */
object BalanceParser {

    // Detect bank keywords so we don't parse random texts
    private val bankHints = listOf(
        "HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "YES BANK", "IDFC",
        "PNB", "BOB", "CANARA", "INDIAN BANK", "UNION BANK", "FEDERAL"
    )

    // Balance regex: matches "Avl Bal", "Available balance", "Avl bal", "Bal" etc.
    // followed by currency (Rs./INR/₹) and the amount (with optional commas/decimals).
    private val balanceRegex = Regex(
        """(?:avl[\s.]*bal(?:ance)?|available[\s.]*bal(?:ance)?|bal(?:ance)?)\s*[:\-]?\s*(?:rs\.?|inr|₹)?\s*([0-9]{1,3}(?:,?[0-9]{2,3})*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // Transaction amount: "Rs.500.00 debited" / "Rs.500.00 credited" / "INR 500 spent"
    private val txnRegex = Regex(
        """(?:rs\.?|inr|₹)\s*([0-9]{1,3}(?:,?[0-9]{2,3})*(?:\.[0-9]{1,2})?)\s*(debited|credited|spent|sent|received|withdrawn|deposited)""",
        RegexOption.IGNORE_CASE
    )

    data class Parsed(
        val balance: Double,
        val txnAmount: Double? = null,
        val direction: Direction = Direction.UNKNOWN,
        val rawText: String,
        val source: String, // "SMS" or "NOTIFICATION"
        val sender: String? = null
    )

    enum class Direction { DEBIT, CREDIT, UNKNOWN }

    fun looksLikeBankMessage(text: String, sender: String? = null): Boolean {
        val haystack = (text + " " + (sender ?: "")).uppercase()
        if (bankHints.any { haystack.contains(it) }) return true
        // fallback: contains "a/c" or "avl bal" — common bank lingo
        return haystack.contains("AVL BAL") || haystack.contains("A/C")
    }

    fun parse(
        text: String,
        source: String,
        sender: String? = null
    ): Parsed? {
        if (!looksLikeBankMessage(text, sender)) return null

        val balMatch = balanceRegex.find(text) ?: return null
        val balance = balMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null

        val txn = txnRegex.find(text)
        val txnAmount = txn?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val direction = when (txn?.groupValues?.get(2)?.lowercase()) {
            "debited", "spent", "sent", "withdrawn" -> Direction.DEBIT
            "credited", "received", "deposited" -> Direction.CREDIT
            else -> Direction.UNKNOWN
        }

        return Parsed(
            balance = balance,
            txnAmount = txnAmount,
            direction = direction,
            rawText = text,
            source = source,
            sender = sender
        )
    }
}
