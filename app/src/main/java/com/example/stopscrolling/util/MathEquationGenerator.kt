package com.example.stopscrolling.util

import kotlin.random.Random

data class MathProblem(
    val firstNumber: Int,
    val secondNumber: Int,
    val operator: Char,
    val expression: String,
    val correctAnswer: Int
)

object MathEquationGenerator {

    private val operators = listOf('+', '-', '*')

    fun generate(random: Random = Random.Default): MathProblem {
        val operator = operators.random(random)

        val (num1, num2) = when (operator) {
            '*' -> {
                val a = random.nextInt(2, 13)
                val b = random.nextInt(2, 13)
                a to b
            }
            '-' -> {
                val a = random.nextInt(1, 50)
                val b = random.nextInt(1, 50)
                if (b > a) b to a else a to b
            }
            else -> {
                val a = random.nextInt(1, 50)
                val b = random.nextInt(1, 50)
                a to b
            }
        }

        val answer = calculateAnswer(num1, num2, operator)

        return MathProblem(
            firstNumber = num1,
            secondNumber = num2,
            operator = operator,
            expression = "$num1 $operator $num2 = ?",
            correctAnswer = answer
        )
    }

    internal fun calculateAnswer(num1: Int, num2: Int, operator: Char): Int {
        return when (operator) {
            '+' -> num1 + num2
            '-' -> num1 - num2
            '*' -> num1 * num2
            else -> throw IllegalArgumentException("Unsupported operator: $operator")
        }
    }
}