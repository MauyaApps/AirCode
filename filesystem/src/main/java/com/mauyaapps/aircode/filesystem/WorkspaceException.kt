package com.mauyaapps.aircode.filesystem

/** Ошибка операции с рабочей папкой. Причина по [reason] переводится в текст на стороне UI. */
class WorkspaceException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(reason.name, cause) {

    enum class Reason {
        /** Пустое имя, «.», «..» или имя с разделителем пути. */
        INVALID_NAME,

        /** Файл или папка с таким именем уже существует. */
        ALREADY_EXISTS,

        /** Файл или папка не найдены. */
        NOT_FOUND,

        /** Путь выходит за пределы рабочей папки. */
        OUTSIDE_WORKSPACE,

        /** Корневую папку нельзя переименовать или удалить. */
        PROTECTED_ROOT,

        /** Файл больше допустимого размера для открытия в редакторе. */
        TOO_LARGE,

        /** Файл не является текстом в кодировке UTF-8. */
        NOT_TEXT,

        /** Системная ошибка ввода-вывода. */
        IO_ERROR,
    }
}
