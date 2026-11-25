package com.example.bmicalci.workout

enum class Muscle(val label: String) {
    CHEST("Chest"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    ABS("Abs"),
    BACK("Back"),
    GLUTES("Glutes"),
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    CALVES("Calves"),
}

data class Exercise(
    val id: String,
    val name: String,
    /** Muscle -> involvement (1.0 = primary mover, lower = assisting). */
    val muscles: Map<Muscle, Float>,
    val met: Float,
    val lowImpact: Boolean,
    val defaultReps: Int,
    val cardio: Boolean = false,
    val equipment: String = "No equipment",
) {
    val primary: Muscle get() = muscles.maxByOrNull { it.value }!!.key
}

/** Everything is doable at home: bodyweight, a chair, or a water bottle. */
object ExerciseCatalog {
    val all: List<Exercise> = listOf(
        Exercise("pushup", "Push-Ups", mapOf(Muscle.CHEST to 1f, Muscle.TRICEPS to .5f, Muscle.SHOULDERS to .4f), 3.8f, true, 12),
        Exercise("incline_pushup", "Incline Push-Ups", mapOf(Muscle.CHEST to 1f, Muscle.TRICEPS to .4f), 3.0f, true, 12, equipment = "Chair / table"),
        Exercise("pike_pushup", "Pike Push-Ups", mapOf(Muscle.SHOULDERS to 1f, Muscle.TRICEPS to .5f), 3.8f, true, 10),
        Exercise("shadow_box", "Shadow Boxing", mapOf(Muscle.SHOULDERS to 1f, Muscle.ABS to .3f), 5.5f, true, 40, cardio = true),
        Exercise("curl", "Bottle Curls", mapOf(Muscle.BICEPS to 1f), 3.0f, true, 12, equipment = "Water bottle"),
        Exercise("chair_dip", "Chair Dips", mapOf(Muscle.TRICEPS to 1f, Muscle.SHOULDERS to .3f, Muscle.CHEST to .3f), 3.5f, true, 10, equipment = "Chair"),
        Exercise("crunch", "Crunches", mapOf(Muscle.ABS to 1f), 3.0f, true, 15),
        Exercise("dead_bug", "Dead Bug", mapOf(Muscle.ABS to 1f), 2.8f, true, 12),
        Exercise("leg_raise", "Leg Raises", mapOf(Muscle.ABS to 1f, Muscle.QUADS to .2f), 3.3f, true, 12),
        Exercise("superman", "Superman", mapOf(Muscle.BACK to 1f, Muscle.GLUTES to .3f), 3.0f, true, 12),
        Exercise("bird_dog", "Bird Dog", mapOf(Muscle.BACK to .8f, Muscle.ABS to .4f, Muscle.GLUTES to .4f), 2.8f, true, 10),
        Exercise("snow_angel", "Reverse Snow Angels", mapOf(Muscle.BACK to 1f, Muscle.SHOULDERS to .4f), 2.8f, true, 12),
        Exercise("bridge", "Glute Bridge", mapOf(Muscle.GLUTES to 1f, Muscle.HAMSTRINGS to .6f), 3.0f, true, 15),
        Exercise("lunge", "Reverse Lunges", mapOf(Muscle.GLUTES to 1f, Muscle.QUADS to .8f, Muscle.HAMSTRINGS to .3f), 4.0f, true, 12),
        Exercise("squat", "Bodyweight Squats", mapOf(Muscle.QUADS to 1f, Muscle.GLUTES to .6f, Muscle.HAMSTRINGS to .3f), 5.0f, true, 15),
        Exercise("chair_squat", "Chair Squats", mapOf(Muscle.QUADS to 1f, Muscle.GLUTES to .5f), 3.5f, true, 12, equipment = "Chair"),
        Exercise("good_morning", "Good Mornings", mapOf(Muscle.HAMSTRINGS to 1f, Muscle.GLUTES to .4f, Muscle.BACK to .3f), 3.0f, true, 12),
        Exercise("calf_raise", "Calf Raises", mapOf(Muscle.CALVES to 1f), 2.8f, true, 20),
        Exercise("jumping_jack", "Jumping Jacks", mapOf(Muscle.CALVES to .5f, Muscle.SHOULDERS to .4f, Muscle.QUADS to .4f), 7.0f, false, 30, cardio = true),
        Exercise("mountain_climber", "Mountain Climbers", mapOf(Muscle.ABS to .6f, Muscle.SHOULDERS to .4f, Muscle.QUADS to .5f), 8.0f, false, 20, cardio = true),
        Exercise("march", "Brisk March In Place", mapOf(Muscle.QUADS to .4f, Muscle.CALVES to .4f), 3.5f, true, 40, cardio = true),
    )

    private val byId = all.associateBy { it.id }
    fun get(id: String): Exercise? = byId[id]
}
