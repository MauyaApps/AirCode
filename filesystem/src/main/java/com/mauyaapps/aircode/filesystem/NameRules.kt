package com.mauyaapps.aircode.filesystem

import com.mauyaapps.aircode.filesystem.WorkspaceException.Reason

object NameRules {

    fun validate(name: String): String {
        val trimmed = name.trim()
        val invalid = trimmed.isEmpty() ||
            trimmed == "." ||
            trimmed == ".." ||
            trimmed.any { it == '/' || it == '\u0000' }
        if (invalid) throw WorkspaceException(Reason.INVALID_NAME)
        return trimmed
    }
}
