package com.example

import com.example.parser.SmsParser
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testBkashReceivedParsing() {
        val sms = "You have received Tk 1,500.00 from 01712345678. Ref 123. Fee Tk 0.00. Balance Tk 5,230.50. TrxID 9K8L7M6N5P at 15/08/2026 14:30"
        val parsed = SmsParser.parseSms("bKash", sms)
        assertNotNull(parsed)
        assertEquals("9K8L7M6N5P", parsed?.trxId)
        assertEquals(1500.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals("bKash", parsed?.method)
        assertEquals("01712345678", parsed?.senderPhone)
        assertEquals(5230.50, parsed?.balance ?: 0.0, 0.001)
    }

    @Test
    fun testNagadReceivedParsing() {
        val sms = "Money Received. Amount: Tk 2,500.00. Sender: 01799887766. TxnID: 7XYZ89AB. Date: 15/08/2026 16:10. Balance: Tk 10,450.00"
        val parsed = SmsParser.parseSms("NAGAD", sms)
        assertNotNull(parsed)
        assertEquals("7XYZ89AB", parsed?.trxId)
        assertEquals(2500.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals("Nagad", parsed?.method)
        assertEquals("01799887766", parsed?.senderPhone)
    }

    @Test
    fun testRocketCashInParsing() {
        val sms = "Cash In from A/C: 017123456789. Tk 3,000.00. TxnId: 1234567890. Balance: Tk 12,000.00"
        val parsed = SmsParser.parseSms("16216", sms)
        assertNotNull(parsed)
        assertEquals("1234567890", parsed?.trxId)
        assertEquals(3000.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals("Rocket", parsed?.method)
    }
}
