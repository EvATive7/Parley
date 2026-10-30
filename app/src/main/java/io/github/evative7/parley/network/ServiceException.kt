package io.github.evative7.parley.network

import java.io.IOException
import java.net.SocketTimeoutException

enum class ServiceFailure { Configuration, Authentication, Permission, RateLimit, Timeout, Network, Response }

/** Intentionally excludes provider bodies, URLs and raw exception messages. [status] is the HTTP code, if any. */
class ServiceException(val failure: ServiceFailure, val status: Int? = null) : IOException(failure.name)

fun httpFailure(code: Int): ServiceException = ServiceException(when (code) {
    401 -> ServiceFailure.Authentication
    403 -> ServiceFailure.Permission
    429 -> ServiceFailure.RateLimit
    408, 504 -> ServiceFailure.Timeout
    else -> ServiceFailure.Response
}, status = code)

fun networkFailure(error: Throwable) = ServiceException(
    if (error is SocketTimeoutException) ServiceFailure.Timeout else ServiceFailure.Network,
)
