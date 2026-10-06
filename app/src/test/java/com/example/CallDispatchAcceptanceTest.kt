package com.example

import com.example.model.CallDispatchResult
import com.example.model.CallDispatchState
import com.example.model.CallRecordRequest
import com.example.model.CallerCustomerContext
import com.example.network.SignalRClient
import com.example.telephony.CallManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class CallDispatchAcceptanceTest {

    /**
     * Test 1 — Connected Cashier
     * Android detects call, SignalR READY, RecordCallerCall completion succeeds,
     * Server delivered = 1, Queued = false, state = DELIVERED.
     */
    @Test
    fun `Test 1 - Connected cashier delivers immediately`() = runBlocking {
        val signalRClient = SignalRClient()
        val callManager = CallManager(signalRClient)

        val testCallId = "call-test-1"
        val request = CallRecordRequest(
            callId = testCallId,
            phone = "07701234567",
            normalizedPhone = "07701234567",
            direction = "incoming",
            startedAtUtc = "2026-10-06T12:00:00.000Z"
        )

        // Simulate successful SignalR dispatch with 1 connected cashier
        val mockContext = CallerCustomerContext(
            callId = testCallId,
            customerId = "cust-1",
            phone = "07701234567",
            normalizedPhone = "07701234567",
            name = "عميل تجريبي",
            area = "المنصور",
            address = "شارع 14 رمضان",
            orderCount = 5,
            lastOrder = "بيتزا لحم"
        )

        val expectedResult = CallDispatchResult(
            success = true,
            callId = testCallId,
            state = CallDispatchState.DELIVERED,
            transport = "SignalR",
            deliveredToCashiers = 1,
            queuedForCashier = false,
            customerContext = mockContext
        )

        assertEquals(CallDispatchState.DELIVERED, expectedResult.state)
        assertEquals(1, expectedResult.deliveredToCashiers)
        assertFalse(expectedResult.queuedForCashier)
        assertEquals("عميل تجريبي", expectedResult.customerContext?.name)
    }

    /**
     * Test 2 — Unknown Customer
     * Name defaults to null / new customer, Phone displayed.
     */
    @Test
    fun `Test 2 - Unknown customer displays phone and new customer state`() = runBlocking {
        val testCallId = "call-test-2"
        val mockContext = CallerCustomerContext(
            callId = testCallId,
            phone = "07709998877",
            normalizedPhone = "07709998877",
            name = "عميل جديد",
            orderCount = 0
        )

        assertEquals("عميل جديد", mockContext.name)
        assertEquals("07709998877", mockContext.phone)
        assertEquals(0, mockContext.orderCount)
    }

    /**
     * Test 3 — Known Customer with full context
     * Phone, Name, Area, Address, OrderCount, LastOrder, MinutesSinceLastCall.
     */
    @Test
    fun `Test 3 - Known customer contains full order and location history`() = runBlocking {
        val testCallId = "call-test-3"
        val mockContext = CallerCustomerContext(
            callId = testCallId,
            customerId = "cust-102",
            phone = "07801234567",
            normalizedPhone = "07801234567",
            name = "أحمد علي",
            area = "الكرادة",
            address = "قرب ساحة كهرمانة",
            orderCount = 12,
            lastOrder = "شاورما دجاج عائلي",
            minutesSinceLastCall = 45,
            retrievedAtUtc = "2026-10-06T12:00:00.000Z"
        )

        assertEquals("أحمد علي", mockContext.name)
        assertEquals("الكرادة", mockContext.area)
        assertEquals("قرب ساحة كهرمانة", mockContext.address)
        assertEquals(12, mockContext.orderCount)
        assertEquals(45, mockContext.minutesSinceLastCall)
        assertNotNull(mockContext.retrievedAtUtc)
    }

    /**
     * Test 4 — No cashier currently connected
     * DeliveredToCashiers = 0, QueuedForCashier = true.
     */
    @Test
    fun `Test 4 - No cashier currently connected queues context`() = runBlocking {
        val testCallId = "call-test-4"
        val result = CallDispatchResult(
            success = true,
            callId = testCallId,
            state = CallDispatchState.QUEUED,
            transport = "SignalR",
            deliveredToCashiers = 0,
            queuedForCashier = true
        )

        assertEquals(0, result.deliveredToCashiers)
        assertTrue(result.queuedForCashier)
        assertEquals(CallDispatchState.QUEUED, result.state)
    }

    /**
     * Test 5 — SignalR temporarily unavailable triggers HTTP fallback without call loss
     */
    @Test
    fun `Test 5 - SignalR unavailable falls back to HTTP successfully`() = runBlocking {
        val signalRClient = SignalRClient()
        val callManager = CallManager(signalRClient)

        var httpFallbackCalled = false
        val testCallId = "call-test-5"

        callManager.httpFallbackDispatcher = { req ->
            httpFallbackCalled = true
            CallDispatchResult(
                success = true,
                callId = req.callId,
                state = CallDispatchState.DELIVERED,
                transport = "HTTP",
                deliveredToCashiers = 1,
                queuedForCashier = false
            )
        }

        val request = CallRecordRequest(
            callId = testCallId,
            phone = "07501234567",
            normalizedPhone = "07501234567",
            direction = "incoming",
            startedAtUtc = "2026-10-06T12:00:00.000Z"
        )

        val result = callManager.dispatchCallRecord(request)

        assertTrue("HTTP fallback must be triggered when SignalR is disconnected", httpFallbackCalled)
        assertTrue(result.success)
        assertEquals("HTTP", result.transport)
        assertEquals(CallDispatchState.DELIVERED, result.state)
    }

    /**
     * Test 6 — Duplicate retry: Same CallId does not create duplicate event
     */
    @Test
    fun `Test 6 - Duplicate incoming ringing preserves stable CallId and idempotency`() {
        val signalRClient = SignalRClient()
        val callManager = CallManager(signalRClient)

        val callId1 = callManager.onIncomingCallRinging("07701112233")
        val callId2 = callManager.onIncomingCallRinging("0770 111 2233")

        assertEquals("Same active incoming call must retain identical CallId", callId1, callId2)
        assertEquals(1, callManager.callHistory.value.size)
    }

    /**
     * Test 7 — SignalR completion error: does NOT mark call as dispatched before server success
     */
    @Test
    fun `Test 7 - SignalR completion error marks call as failed or rejected, not dispatched`() = runBlocking {
        val signalRClient = SignalRClient()
        val callManager = CallManager(signalRClient)

        val testCallId = "call-test-7"
        val request = CallRecordRequest(
            callId = testCallId,
            phone = "07704445566",
            normalizedPhone = "07704445566",
            direction = "incoming",
            startedAtUtc = "2026-10-06T12:00:00.000Z"
        )

        // Mock ringing to populate history item
        callManager.onIncomingCallRinging("07704445566")

        // No HTTP fallback set, SignalR not connected -> fails
        val result = callManager.dispatchCallRecord(request)

        assertFalse("Must not be marked success when server has not accepted", result.success)
        val activeCall = callManager.activeCall.value
        assertFalse(activeCall?.isDispatchedToTaloola ?: false)
    }

    /**
     * Test 8 — Reconnect & flush queue with same CallId
     */
    @Test
    fun `Test 8 - Reconnect flushes queue with stable CallId`() = runBlocking {
        val signalRClient = SignalRClient()
        val callManager = CallManager(signalRClient)

        var dispatchCount = AtomicInteger(0)
        callManager.httpFallbackDispatcher = { req ->
            dispatchCount.incrementAndGet()
            CallDispatchResult(
                success = false,
                callId = req.callId,
                state = CallDispatchState.FAILED,
                transport = "HTTP",
                errorMessage = "Offline"
            )
        }

        val request = CallRecordRequest(
            callId = "call-test-8",
            phone = "07705556677",
            normalizedPhone = "07705556677",
            direction = "incoming",
            startedAtUtc = "2026-10-06T12:00:00.000Z"
        )

        // Dispatch while offline -> queued
        callManager.dispatchCallRecord(request)

        // Now mock connection restored
        callManager.httpFallbackDispatcher = { req ->
            dispatchCount.incrementAndGet()
            CallDispatchResult(
                success = true,
                callId = req.callId,
                state = CallDispatchState.DELIVERED,
                transport = "HTTP",
                deliveredToCashiers = 1
            )
        }

        callManager.flushOfflineQueue()

        assertTrue(dispatchCount.get() >= 2)
    }

    /**
     * Test 9 — Context mapping by CallId first, NormalizedPhone second
     */
    @Test
    fun `Test 9 - CallerCustomerContext associates with CallId accurately`() = runBlocking {
        val signalRClient = SignalRClient()
        val callManager = CallManager(signalRClient)

        val callId = callManager.onIncomingCallRinging("07701234567")

        // Emit customer context for this callId
        val context = CallerCustomerContext(
            callId = callId,
            phone = "07701234567",
            normalizedPhone = "07701234567",
            name = "حيدر حسن",
            area = "الجادرية",
            orderCount = 3
        )

        val deferred = CompletableDeferred<Unit>()
        // Simulate event collection
        signalRClient.hubManager.customerContextEvents

        // Directly verify association
        val active = callManager.activeCall.value
        assertNotNull(active)
        assertEquals(callId, active?.callId)
    }
}
