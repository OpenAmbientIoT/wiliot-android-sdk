package com.wiliot.wiliotcore.model

@Suppress("unused")
sealed class Ack {

    /**
     * Message contains Acknowledge data for the Bridge OTA upgrade job.
     *
     * @param [action] contains OTA action constant (1);
     * @param [statusCode] contains status for the OTA upgrade job (success -> 0, failure -> 1).
     *
     * Class has private constructor, so use [success] or [failure] methods to create payload.
     */
    class OtaJobAck private constructor(
        val action: Int = 1,
        val statusCode: Int = 1
    ): Ack() {
        companion object {
            fun success(): OtaJobAck = OtaJobAck(
                statusCode = 0
            )

            fun failure(): OtaJobAck = OtaJobAck(
                statusCode = 1
            )
        }
    }

}