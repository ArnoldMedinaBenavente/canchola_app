package com.canchola.models

import java.io.Serializable

data class SyncDataResponse(
    val customers: List<Customer>,
    val concepts: List<Concept>,
    val units: List<MeasurementUnit>
) : Serializable
