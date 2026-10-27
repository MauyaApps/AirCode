package com.mauyaapps.aircode

import com.mauyaapps.aircode.filesystem.WorkspaceException

/** Строка с текстом ошибки для причины сбоя операции с файлами. */
internal fun workspaceErrorRes(reason: WorkspaceException.Reason): Int = when (reason) {
    WorkspaceException.Reason.INVALID_NAME -> R.string.error_invalid_name
    WorkspaceException.Reason.ALREADY_EXISTS -> R.string.error_exists
    WorkspaceException.Reason.NOT_FOUND -> R.string.error_not_found
    WorkspaceException.Reason.OUTSIDE_WORKSPACE -> R.string.error_outside
    WorkspaceException.Reason.PROTECTED_ROOT -> R.string.error_root
    WorkspaceException.Reason.TOO_LARGE -> R.string.error_too_large
    WorkspaceException.Reason.NOT_TEXT -> R.string.error_not_text
    WorkspaceException.Reason.IO_ERROR -> R.string.error_io
}
