package com.example.bmicalci

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // Declaring views
    private lateinit var weightInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var calculateButton: Button
    private lateinit var bmiValueText: TextView
    private lateinit var bmiCategoryText: TextView
    private lateinit var infoText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initializing views
        weightInput = findViewById(R.id.ppweight)
        heightInput = findViewById(R.id.ppheight)
        calculateButton = findViewById(R.id.cvbutton)
        bmiValueText = findViewById(R.id.abindex)
        bmiCategoryText = findViewById(R.id.abresult)
        infoText = findViewById(R.id.abinfo)

        // Click listener
        calculateButton.setOnClickListener {
            val weight = weightInput.text.toString()
            val height = heightInput.text.toString()

            if (validateInput(weight, height)) {
                val bmi = calculateBMI(weight, height)
                displayResult(bmi)
                clearInputs()
            }
        }
    }

    // Validate that inputs are not empty
    private fun validateInput(weight: String?, height: String?): Boolean {
        return when {
            weight.isNullOrEmpty() -> {
                Toast.makeText(this, "Please enter your weight!", Toast.LENGTH_SHORT).show()
                false
            }
            height.isNullOrEmpty() -> {
                Toast.makeText(this, "Please enter your height!", Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }

    // BMI calculation logic
    private fun calculateBMI(weight: String, height: String): Float {
        val weightInKg = weight.toFloat()
        val heightInMeters = height.toFloat() / 100
        val bmi = weightInKg / (heightInMeters * heightInMeters)
        return String.format("%.2f", bmi).toFloat()
    }

    // Show result with color and label
    private fun displayResult(bmi: Float) {
        bmiValueText.text = bmi.toString()
        infoText.text = "Normal BMI range is 18.5 - 24.9"

        var resultLabel = ""
        var color = 0

        when {
            bmi < 18.5 -> {
                resultLabel = "Underweight"
                color = R.color.underWeight
            }
            bmi in 18.5..24.99 -> {
                resultLabel = "Healthy"
                color = R.color.normal
            }
            bmi in 25.0..29.99 -> {
                resultLabel = "Overweight"
                color = R.color.overweight
            }
            bmi > 29.99 -> {
                resultLabel = "Obese"
                color = R.color.mota
            }
        }

        // Set result text and color
        bmiCategoryText.text = resultLabel
        bmiCategoryText.setTextColor(ContextCompat.getColor(this, color))
    }

    // Optional: Clear input fields
    private fun clearInputs() {
        weightInput.text.clear()
        heightInput.text.clear()
    }
}
