package app.duinch.agentremote.core.domain.result

sealed interface AppResult<out T, out E> {
    data class Success<out T>(val data: T) : AppResult<T, Nothing>
    data class Failure<out E>(val error: E) : AppResult<Nothing, E>

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }
}

inline fun <T, E, R> AppResult<T, E>.map(transform: (T) -> R): AppResult<R, E> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T, E, R> AppResult<T, E>.fold(onSuccess: (T) -> R, onFailure: (E) -> R): R = when (this) {
    is AppResult.Success -> onSuccess(data)
    is AppResult.Failure -> onFailure(error)
}
