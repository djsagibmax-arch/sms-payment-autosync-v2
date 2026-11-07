package com.example.util

object PhoneNumberSanitizer {

    /**
     * Sanitizes a phone number for WhatsApp messaging.
     * 1. Strips all non-digit characters.
     * 2. Enforces Bangladesh country code 880 (e.g., 017... -> 88017..., +88017... -> 88017...).
     * 3. Returns null if invalid or cannot be formatted.
     */
    fun sanitize(rawNumber: String?): String? {
        if (rawNumber.isNullOrBlank()) return null

        // 1. Strip all non-numeric characters (spaces, +, -, parentheses, etc.)
        val digitsOnly = rawNumber.replace(Regex("[^0-9]"), "")
        if (digitsOnly.length < 10) return null

        // 2. Handle international prefix cases
        return when {
            // Case A: Starts with 00880... (e.g., 008801712345678)
            digitsOnly.startsWith("00880") -> digitsOnly.removePrefix("00")

            // Case B: Starts with 880 (standard full BD format e.g., 8801712345678)
            digitsOnly.startsWith("880") && digitsOnly.length == 13 -> digitsOnly

            // Case C: Starts with 0 (e.g., 01712345678 -> prefix '88' to make 8801712345678)
            digitsOnly.startsWith("0") && digitsOnly.length == 11 -> "88$digitsOnly"

            // Case D: 10 digits starting with 1 (e.g., 1712345678 -> prefix '880')
            digitsOnly.startsWith("1") && digitsOnly.length == 10 -> "880$digitsOnly"

            // Case E: Already 880 with 13 digits or international number with 10-15 digits
            digitsOnly.startsWith("880") -> digitsOnly

            // If starts with 0 and length is more than 11 (e.g. 017123456789)
            digitsOnly.startsWith("0") -> "88$digitsOnly"

            // Fallback for valid international numbers (10 to 15 digits)
            digitsOnly.length in 10..15 -> digitsOnly

            else -> null
        }
    }

    /**
     * Checks if a phone number can be sanitized to a valid WhatsApp recipient.
     */
    fun isValid(rawNumber: String?): Boolean {
        val sanitized = sanitize(rawNumber)
        return sanitized != null && sanitized.length in 11..15
    }
}
