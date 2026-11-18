package com.example.stopscrolling

import com.example.stopscrolling.ui.mathquiz.MathQuizError
import com.example.stopscrolling.ui.mathquiz.MathQuizViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathQuizViewModelTest {

    @Test
    fun submitEmptyInput_setsInvalidNumberError() {
        val viewModel = MathQuizViewModel()
        val result = viewModel.onSubmitAnswer()

        assertFalse(result)
        assertEquals(MathQuizError.INVALID_NUMBER, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSolved)
    }

    @Test
    fun submitWrongAnswer_setsWrongAnswerError() {
        val viewModel = MathQuizViewModel()
        val wrongAnswer = viewModel.uiState.value.problem.correctAnswer + 1
        viewModel.onAnswerChange(wrongAnswer.toString())

        val result = viewModel.onSubmitAnswer()

        assertFalse(result)
        assertEquals(MathQuizError.WRONG_ANSWER, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSolved)
    }

    @Test
    fun submitCorrectAnswer_marksQuizSolved() {
        val viewModel = MathQuizViewModel()
        val correctAnswer = viewModel.uiState.value.problem.correctAnswer
        viewModel.onAnswerChange(correctAnswer.toString())

        val result = viewModel.onSubmitAnswer()

        assertTrue(result)
        assertEquals(MathQuizError.NONE, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.isSolved)
    }
}