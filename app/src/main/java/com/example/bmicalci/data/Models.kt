package com.example.bmicalci.data

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val weight: Float,
    val height: Float,
    val waist: Float,
    val age: Int,
    val isMale: Boolean = true,
    val isMetric: Boolean = true,
)

/** One performed set inside a finished workout. */
@Serializable
data class SetLog(
    val exerciseId: String,
    val reps: Int,
)

@Serializable
data class SessionRecord(
    val timestamp: Long,
    val durationSec: Int,
    val sets: List<SetLog>,
    /** Muscle group name -> load produced by this session. */
    val muscleLoad: Map<String, Float>,
    val caloriesBurned: Int,
    val waterMl: Int = 0,
)
