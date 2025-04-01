package com.wiliot.wiliotcore.contracts

import com.wiliot.wiliotcore.model.Ack

/**
 * Upstream feedback channel to add Ack payloads to upload queue.
 * See related [com.wiliot.wiliotcore.contracts.MessageQueueManagerContract.publishMelAck]
 */
interface UpstreamFeedbackChannel {
    /**
     * Used to enqueue GW-generated Acknowledge message sending
     */
    fun sendAck(ackPayload: Ack)
}