package com.example.parser

import com.example.model.PaymentData
import java.util.regex.Pattern

object SmsParser {

    /**
     * Determines the payment method/wallet based on sender ID and message content.
     */
    fun detectPaymentMethod(senderId: String, body: String): String {
        val s = senderId.uppercase()
        val b = body.uppercase()

        return when {
            s.contains("BKASH") || s.contains("16247") || b.contains("BKASH") -> "bKash"
            s.contains("NAGAD") || s.contains("16167") || b.contains("NAGAD") -> "Nagad"
            s.contains("ROCKET") || s.contains("16216") || s.contains("DBBL") || b.contains("ROCKET") -> "Rocket"
            s.contains("UPAY") || s.contains("16268") || b.contains("UPAY") -> "Upay"
            s.contains("CELLFIN") || s.contains("IBBL") || b.contains("CELLFIN") -> "Cellfin"
            else -> "MFS"
        }
    }

    /**
     * Checks if the sender matches configured active wallets or custom keywords.
     */
    fun isSenderAllowed(
        senderId: String,
        body: String,
        bkashEnabled: Boolean,
        nagadEnabled: Boolean,
        rocketEnabled: Boolean,
        upayEnabled: Boolean,
        cellfinEnabled: Boolean,
        customKeywords: String
    ): Boolean {
        val method = detectPaymentMethod(senderId, body)
        if (method == "bKash" && bkashEnabled) return true
        if (method == "Nagad" && nagadEnabled) return true
        if (method == "Rocket" && rocketEnabled) return true
        if (method == "Upay" && upayEnabled) return true
        if (method == "Cellfin" && cellfinEnabled) return true

        // Check custom keywords
        if (customKeywords.isNotBlank()) {
            val keywords = customKeywords.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
            val senderUpper = senderId.uppercase()
            val bodyUpper = body.uppercase()
            for (kw in keywords) {
                if (senderUpper.contains(kw) || bodyUpper.contains(kw)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Core Regex-based payment parser.
     * Extracts TrxID, Amount, Sender Phone, Reference, Balance, and Transaction Type.
     */
    fun parseSms(senderId: String, body: String, timestamp: Long = System.currentTimeMillis()): PaymentData? {
        val method = detectPaymentMethod(senderId, body)

        // Extract TrxID
        val trxId = extractTrxId(body) ?: return null

        // Extract Amount
        val amount = extractAmount(body) ?: return null

        // Extract Sender Phone
        val senderPhone = extractSenderPhone(body)

        // Extract Balance
        val balance = extractBalance(body)

        // Extract Reference
        val reference = extractReference(body)

        // Extract Transaction Type
        val transactionType = determineTransactionType(body)

        return PaymentData(
            trxId = trxId,
            amount = amount,
            currency = "BDT",
            method = method,
            senderPhone = senderPhone,
            senderId = senderId,
            rawMessage = body,
            timestamp = timestamp,
            reference = reference,
            balance = balance,
            transactionType = transactionType
        )
    }

    fun extractTrxId(body: String): String? {
        // Look for TrxID, TxnID, Trx ID, Txn Id, TRX ID, Transaction ID, ID:, Txn:, Trx:
        val patterns = listOf(
            Pattern.compile("""(?:TrxID|TxnID|Trx\s*ID|Txn\s*Id|TRX\s*ID|Transaction\s*ID|Txn\s*no|Trans\s*ID|Trx|Txn|ID)[:=\s]+([A-Za-z0-9_]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:TrxID|TxnID|TrxId|TxnId)\s*[:=\s]+([A-Za-z0-9_]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(?:TrxID|TxnID)\b\s*([A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""ID[:\s]+([A-Za-z0-9]{6,16})""", Pattern.CASE_INSENSITIVE),
            // Fallback: standalone 8-14 char uppercase alphanumeric token
            Pattern.compile("""\b([A-Z0-9]{8,14})\b""")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val match = matcher.group(1)?.trim()
                if (!match.isNullOrEmpty() && match.length >= 4) {
                    val cleaned = match.trimEnd('.', ',', ';', ' ', ':')
                    if (cleaned.any { it.isDigit() } && cleaned.any { it.isLetter() }) {
                        return cleaned
                    } else if (cleaned.length >= 6) {
                        return cleaned
                    }
                }
            }
        }
        return null
    }

    fun extractAmount(body: String): Double? {
        val patterns = listOf(
            // Tk 1,500.00 or Tk. 1500 or BDT 1,250.50 or Amount: Tk 500 or ৳ 500
            Pattern.compile("""(?:Amount\s*[:=\s]*)?(?:Tk|BDT|Tk\.|Tk\s*[:]|\$|৳)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
            // 500.00 Tk or 1200 BDT
            Pattern.compile("""([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:Tk|BDT|৳)""", Pattern.CASE_INSENSITIVE),
            // Amount: 1,500.00
            Pattern.compile("""Amount\s*[:=\s]+([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
            // received 500.00 BDT or Tk
            Pattern.compile("""(?:received|received\s+payment|cash\s+in|fee|transfer)\s+(?:of\s+)?(?:Tk\.?|BDT)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
            // Standalone number formatted like money: 1,200.00
            Pattern.compile("""\b([0-9]{1,3}(?:,[0-9]{3})+(?:\.[0-9]{1,2})?)\b""")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val rawVal = matcher.group(1)?.replace(",", "")?.trim()
                val parsed = rawVal?.toDoubleOrNull()
                if (parsed != null && parsed > 0.0) {
                    return parsed
                }
            }
        }
        return null
    }

    fun extractSenderPhone(body: String): String? {
        val patterns = listOf(
            // from 01712345678 or From: +8801812345678 or from A/C: 01712345678
            Pattern.compile("""(?:from\s+A\/C|from\s+account|from|sender\s*[:=]|sender)\s*[:\s]*(\+?8801[0-9]{9}|01[0-9]{9})""", Pattern.CASE_INSENSITIVE),
            // UCB / Cellfin A/C 017...
            Pattern.compile("""A\/C\s*[:\s]*(\+?8801[0-9]{9}|01[0-9]{9})""", Pattern.CASE_INSENSITIVE),
            // Standard Bangladeshi Mobile Number anywhere after keywords
            Pattern.compile("""(?:from|by|sender)\s+([0-9]{11,14})""", Pattern.CASE_INSENSITIVE),
            // Any 11-digit Bangladeshi mobile number starting with 01
            Pattern.compile("""\b(01[3-9][0-9]{8})\b""")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val phone = matcher.group(1)?.trim()
                if (!phone.isNullOrEmpty()) {
                    return phone
                }
            }
        }
        return null
    }

    fun extractBalance(body: String): Double? {
        val patterns = listOf(
            Pattern.compile("""(?:Balance|New\s+Balance|Available\s+Bal|Bal)\s*[:\s]*(?:Tk\.?|BDT)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""Balance\s+Tk\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val rawVal = matcher.group(1)?.replace(",", "")?.trim()
                val parsed = rawVal?.toDoubleOrNull()
                if (parsed != null) {
                    return parsed
                }
            }
        }
        return null
    }

    fun extractReference(body: String): String? {
        val pattern = Pattern.compile("""(?:Ref|Reference)\s*[:=\s]+([^.\n\r]+?)(?=\s+(?:Fee|Balance|TrxID|TxnID|at|$|\.))""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(body)
        if (matcher.find()) {
            val ref = matcher.group(1)?.trim()
            if (!ref.isNullOrBlank()) {
                return ref
            }
        }
        return null
    }

    fun determineTransactionType(body: String): String {
        val lower = body.lowercase()
        return when {
            lower.contains("cash in") -> "Cash In"
            lower.contains("payment received") || lower.contains("received payment") -> "Payment Received"
            lower.contains("received") || lower.contains("money received") -> "Money Received"
            lower.contains("send money") || lower.contains("money transferred") -> "Send Money"
            lower.contains("merchant payment") -> "Merchant Payment"
            lower.contains("recharge") -> "Mobile Recharge"
            else -> "Payment"
        }
    }
}
