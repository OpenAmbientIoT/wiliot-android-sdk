package com.wiliot.wiliotcore.model

import java.io.Serializable
import java.util.Date

class PackedData(
    val acceleration: Acceleration?,
    val location: Location?,
    val tagId: String?,
    val payload: String?,
    val sequenceId: Long?,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable