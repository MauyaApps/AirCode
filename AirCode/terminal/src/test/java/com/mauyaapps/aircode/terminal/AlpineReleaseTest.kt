package com.mauyaapps.aircode.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlpineReleaseTest {

    private val yaml = """
        ---
        -
          title: "Mini root filesystem"
          desc: "Minimal root filesystem for containers"
          file: alpine-minirootfs-3.22.1-aarch64.tar.gz
          size: 3624021
          sha256: aaaa1111
          sha512: bbbb2222
          flavor: alpine-minirootfs
          branch: v3.22
        -
          title: "Netboot"
          file: alpine-netboot-3.22.1-aarch64.tar.gz
          sha256: cccc3333
          flavor: alpine-netboot
    """.trimIndent()

    @Test
    fun parsesMinirootfsEntry() {
        val release = AlpineRelease.parse(yaml)
        assertEquals(RootfsRelease("alpine-minirootfs-3.22.1-aarch64.tar.gz", "aaaa1111"), release)
    }

    @Test
    fun parsesInlineFirstKeyAndQuotes() {
        val text = "- title: x\n  file: \"alpine-minirootfs-3.0-x86_64.tar.gz\"\n  sha256: '1234'\n  flavor: alpine-minirootfs\n"
        assertEquals(RootfsRelease("alpine-minirootfs-3.0-x86_64.tar.gz", "1234"), AlpineRelease.parse(text))
    }

    @Test
    fun missingFlavorGivesNull() {
        assertNull(AlpineRelease.parse("-\n  file: a.tar.gz\n  sha256: 1\n  flavor: other\n"))
        assertNull(AlpineRelease.parse(""))
    }

    @Test
    fun abiMapping() {
        assertEquals("aarch64", AlpineRelease.archFor(arrayOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("x86_64", AlpineRelease.archFor(arrayOf("x86_64", "x86")))
        assertEquals("armv7", AlpineRelease.archFor(arrayOf("armeabi-v7a")))
        assertNull(AlpineRelease.archFor(arrayOf("mips")))
    }

    @Test
    fun urls() {
        assertEquals(
            "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/aarch64/latest-releases.yaml",
            AlpineRelease.listingUrl("aarch64"),
        )
        assertEquals(
            "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/aarch64/f.tar.gz",
            AlpineRelease.fileUrl("aarch64", RootfsRelease("f.tar.gz", "x")),
        )
    }
}
