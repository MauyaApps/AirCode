package com.mauyaapps.aircode.filesystem

class WorkspaceException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(reason.name, cause) {

    enum class Reason {
        INVALID_NAME,

        ALREADY_EXISTS,

        NOT_FOUND,

        OUTSIDE_WORKSPACE,

        PROTECTED_ROOT,

        TOO_LARGE,

        NOT_TEXT,

        IO_ERROR,
    }
}
