package com.example.stopscrolling

import com.example.stopscrolling.util.MathEquationGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MathEquationGeneratorTest {

    @Test
    fun generate_producesValidEquationsAndCorrectAnswers() {
        val seededRandom = Random(42)
        repeat(100) {
            val problem = MathEquationGenerator.generate(seededRandom)
            val expected = when (problem.operator) {
                '+' -> problem.firstNumber + problem.secondNumber
                '-' -> problem.firstNumber - problem.secondNumber
                '*' -> problem.firstNumber * problem.secondNumber
                else -> throw AssertionError("Unexpected operator: ${problem.operator}")
            }
            assertEquals(expected, problem.correctAnswer)
            assertTrue("Answer should never be negative", problem.correctAnswer >= 0)
            assertEquals(
                "${problem.firstNumber} ${problem.operator} ${problem.secondNumber} = ?",
                problem.expression
            )
        }
    }

    @Test
    fun calculateAnswer_handlesAllOperatorsCorrectly() {
        assertEquals(15, MathEquationGenerator.calculateAnswer(7, 8, '+'))
        assertEquals(6, MathEquationGenerator.calculateAnswer(14, 8, '-'))
        assertEquals(56, MathEquationGenerator.calculateAnswer(7, 8, '*'))
    }
}