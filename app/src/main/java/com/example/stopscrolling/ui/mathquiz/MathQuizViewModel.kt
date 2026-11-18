package com.example.stopscrolling.ui.mathquiz

import androidx.lifecycle.ViewModel
import com.example.stopscrolling.util.MathEquationGenerator
import com.example.stopscrolling.util.MathProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class MathQuizError {
    NONE,
    INVALID_NUMBER,
    WRONG_ANSWER
}

data class MathQuizUiState(
    val problem: MathProblem = MathEquationGenerator.generate(),
    val userAnswerInput: String = "",
    val error: MathQuizError = MathQuizError.NONE,
    val isSolved: Boolean = false
)

class MathQuizViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MathQuizUiState())
    val uiState: StateFlow<MathQuizUiState> = _uiState.asStateFlow()

    fun onAnswerChange(input: String) {
        val filtered = input.filter { it.isDigit() }
        _uiState.update {
            it.copy(userAnswerInput = filtered, error = MathQuizError.NONE)
        }
    }

    fun onSubmitAnswer(): Boolean {
        val currentState = _uiState.value
        val parsedAnswer = currentState.userAnswerInput.trim().toIntOrNull()

        if (parsedAnswer == null) {
            _uiState.update { it.copy(error = MathQuizError.INVALID_NUMBER) }
            return false
        }

        return if (parsedAnswer == currentState.problem.correctAnswer) {
            _uiState.update { it.copy(isSolved = true, error = MathQuizError.NONE) }
            true
        } else {
            _uiState.update { it.copy(error = MathQuizError.WRONG_ANSWER) }
            false
        }
    }

    fun resetQuiz() {
        _uiState.value = MathQuizUiState(problem = MathEquationGenerator.generate())
    }
}