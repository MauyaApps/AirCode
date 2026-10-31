package com.mauyaapps.aircode.terminal

data class RootfsRelease(val file: String, val sha256: String)

object AlpineRelease {

    private const val FLAVOR = "alpine-minirootfs"

    fun archFor(abis: Array<String>): String? = abis.firstNotNullOfOrNull { abi ->
        when (abi) {
            "arm64-v8a" -> "aarch64"
            "x86_64" -> "x86_64"
            "armeabi-v7a" -> "armv7"
            else -> null
        }
    }

    fun baseUrl(arch: String): String =
        "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/$arch"

    fun listingUrl(arch: String): String = "${baseUrl(arch)}/latest-releases.yaml"

    fun fileUrl(arch: String, release: RootfsRelease): String = "${baseUrl(arch)}/${release.file}"

    fun parse(yaml: String): RootfsRelease? {
        val entries = ArrayList<MutableMap<String, String>>()
        for (raw in yaml.lineSequence()) {
            var line = raw.trim()
            if (line.isEmpty() || line == "---") continue
            if (line == "-" || line.startsWith("- ")) {
                entries.add(HashMap())
                line = line.removePrefix("-").trim()
                if (line.isEmpty()) continue
            }
            val current = entries.lastOrNull() ?: continue
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim().trim('"', '\'')
            current.putIfAbsent(key, value)
        }
        return entries
            .firstOrNull { it["flavor"] == FLAVOR && !it["file"].isNullOrEmpty() && !it["sha256"].isNullOrEmpty() }
            ?.let { RootfsRelease(file = it.getValue("file"), sha256 = it.getValue("sha256")) }
    }
}
