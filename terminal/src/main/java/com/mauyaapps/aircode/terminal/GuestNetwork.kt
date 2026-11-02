package com.mauyaapps.aircode.terminal

object GuestNetwork {

    const val MIRROR_HOST = "dl-cdn.alpinelinux.org"

    private val fallbackDns = listOf("1.1.1.1", "8.8.8.8")
    private const val MAX_NAMESERVERS = 3

    fun resolvConf(androidServers: List<String>): String {
        val usable = androidServers
            .map { it.trim().substringBefore('%') }
            .filter { it.isNotEmpty() && !isUnusable(it) }
        val servers = (usable.take(2) + fallbackDns).distinct().take(MAX_NAMESERVERS)
        return servers.joinToString(separator = "") { "nameserver $it\n" } + "options timeout:3 attempts:2\n"
    }

    fun hostsFile(pins: Map<String, List<String>> = emptyMap()): String {
        val base = "127.0.0.1 localhost localhost.localdomain\n::1 localhost ip6-localhost\n"
        val extra = pins.entries.joinToString(separator = "") { (host, addresses) ->
            addresses.joinToString(separator = "") { "$it $host\n" }
        }
        return base + extra
    }

    fun httpRepositories(repositories: String): String =
        repositories.lines().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("https://")) "http://" + trimmed.removePrefix("https://") else line
        }

    private fun isUnusable(address: String): Boolean {
        val lower = address.lowercase()
        return lower.startsWith("127.") || lower == "0.0.0.0" || lower == "::" || lower == "::1" ||
            lower.startsWith("fe80")
    }
}
