package com.hfspaces.explorer.ui.common

import com.hfspaces.explorer.data.remote.HttpStatusException
import com.hfspaces.explorer.data.remote.SpaceExecutionException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** A short, human message for an exception shown in the UI. */
fun Throwable.userMessage(): String = when (this) {
    is UnknownHostException, is ConnectException -> "No connection. Check your internet and try again."
    is InterruptedIOException -> "The request timed out. The Space may be busy or waking up."
    is SSLException -> "A secure connection couldn't be established."
    is HttpStatusException, is SpaceExecutionException -> message ?: "Request failed"
    else -> message?.takeIf { it.isNotBlank() } ?: "Something went wrong"
}
