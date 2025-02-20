import cn.hutool.core.util.ZipUtil
import cn.hutool.crypto.digest.DigestUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.gradle.api.Project
import org.kohsuke.github.GitHubBuilder
import java.io.File
import java.util.*

fun Project.downloadAssets() {
    val assets = File(projectDir, "src/main/assets")
    val downloader = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()

    val github = GitHubBuilder().build()
    val geoip = github.getRepository("v2fly/geoip").latestRelease
    val geoipFile = File(assets, "v2ray/geoip.dat")
    val geoipXzFile = File(assets, "v2ray/geoip.dat.xz")
    val geoipVersion = File(assets, "v2ray/geoip.version.txt")
    if (!geoipVersion.isFile || geoipVersion.readText() != geoip.tagName) {
        geoipVersion.deleteRecursively()
        geoipFile.parentFile.mkdirs()

        val geoipDat = (geoip.listAssets().toSet().find { it.name == geoipFile.name }
            ?: error("${geoipFile.name} not found in ${geoip.assetsUrl}")).browserDownloadUrl

        val sha256sum = (geoip.listAssets().toSet().find { it.name == "geoip.dat.sha256sum" }
            ?: error("geoip.dat.sha256sum not found in ${geoip.assetsUrl}")).browserDownloadUrl

        println("Downloading $sha256sum ...")

        val checksum = downloader.newCall(
            Request.Builder().url(sha256sum).build()
        )
            .execute()
            .let { it.body ?: error("Error when downloading $sha256sum: $it") }
            .string()
            .trim()
            .substringBefore(" ")
            .toUpperCase(Locale.ROOT)
        var count = 0

        while (true) {
            count++

            println("Downloading $geoipDat ...")

            downloader.newCall(
                Request.Builder().url(geoipDat).build()
            )
                .execute()
                .let { it.body ?: error("Error when downloading $geoipDat: $it") }
                .byteStream()
                .use {
                    geoipFile.outputStream().use { out -> it.copyTo(out) }
                }

            val fileSha256 = DigestUtil.sha256Hex(geoipFile).toUpperCase(Locale.ROOT)
            if (fileSha256 != checksum) {
                System.err.println(
                    "Error verifying ${geoipFile.name}: \nLocal: ${
                        fileSha256.toUpperCase(
                            Locale.ROOT
                        )
                    }\nRemote: $checksum"
                )
                if (count > 3) error("Exit")
                System.err.println("Retrying...")
                continue
            }

            geoipFile.inputStream().use { input ->
                XZCompressorOutputStream(geoipXzFile.outputStream(), 9).use {
                    input.copyTo(it)
                }
            }
            geoipFile.delete()
            geoipVersion.writeText(geoip.tagName)

            break
        }
    }

    val geosite = github.getRepository("v2fly/domain-list-community").latestRelease
    val geositeFile = File(assets, "v2ray/geosite.dat")
    val geositeXzFile = File(assets, "v2ray/geosite.dat.xz")
    val geositeVersion = File(assets, "v2ray/geosite.version.txt")
    if (!geositeVersion.isFile || geositeVersion.readText() != geosite.tagName) {
        geositeVersion.deleteRecursively()

        val geositeDat = (geosite.listAssets().toSet().find { it.name == "dlc.dat" }
            ?: error("dlc.dat not found in ${geosite.assetsUrl}")).browserDownloadUrl

        val sha256sum = (geosite.listAssets().toSet().find { it.name == "dlc.dat.sha256sum" }
            ?: error("dlc.dat.sha256sum not found in ${geosite.assetsUrl}")).browserDownloadUrl

        println("Downloading $sha256sum ...")

        val checksum = downloader.newCall(
            Request.Builder().url(sha256sum).build()
        )
            .execute()
            .let { it.body ?: error("Error when downloading $sha256sum: $it") }
            .string()
            .trim()
            .substringBefore(" ")
            .toUpperCase(Locale.ROOT)

        var count = 0

        while (true) {
            count++

            println("Downloading $geositeDat ...")

            downloader.newCall(
                Request.Builder().url(geositeDat).build()
            )
                .execute()
                .let { it.body ?: error("Error when downloading $geositeDat: $it") }
                .byteStream()
                .use {
                    geositeFile.outputStream().use { out -> it.copyTo(out) }
                }

            val fileSha256 = DigestUtil.sha256Hex(geositeFile).toUpperCase(Locale.ROOT)
            if (fileSha256 != checksum) {
                System.err.println(
                    "Error verifying ${geositeFile.name}: \nLocal: ${
                        fileSha256.toUpperCase(
                            Locale.ROOT
                        )
                    }\nRemote: $checksum"
                )
                if (count > 3) error("Exit")
                System.err.println("Retrying...")
                continue
            }

            geositeFile.inputStream().use { input ->
                XZCompressorOutputStream(geositeXzFile.outputStream(), 9).use {
                    input.copyTo(it)
                }
            }
            geositeFile.delete()
            geositeVersion.writeText(geosite.tagName)

            break
        }
    }

    val v2rayVersion = File(rootDir, "external/v2ray-core/core.go").readText()
        .substringAfter("version")
        .substringAfter("\"")
        .substringBefore("\"")
        .let { "v$it" }
    val coreVersionFile = File(assets, "v2ray/core.version.txt")
    val cacheFile = File(rootProject.buildDir, "v2ray-extra.zip")
    cacheFile.parentFile.mkdirs()
    cacheFile.deleteRecursively()

    if (!coreVersionFile.isFile || coreVersionFile.readText() != v2rayVersion) {
        val v2rayCore = github.getRepository("v2fly/v2ray-core").getReleaseByTagName(v2rayVersion)
            ?: error("Tag $v2rayVersion not found in v2ray-core")

        val v2rayExtraZip = (v2rayCore.listAssets().find { it.name == "v2ray-extra.zip" }
            ?: error("v2ray-extra.zip not found in ${v2rayCore.assetsUrl}")).browserDownloadUrl

        println("Downloading $v2rayExtraZip ...")

        downloader.newCall(
            Request.Builder().url(v2rayExtraZip).build()
        )
            .execute()
            .let { it.body ?: error("Error when downloading $v2rayExtraZip: $it") }
            .byteStream()
            .use {
                cacheFile.outputStream().use { out -> it.copyTo(out) }
            }

        ZipUtil.get(cacheFile, null, "browserforwarder/index.js").use {
            File(assets, "v2ray/index.js.xz").outputStream().use { out ->
                it.copyTo(XZCompressorOutputStream(out, 9))
            }
        }

        ZipUtil.get(cacheFile, null, "browserforwarder/index.html").use {
            File(assets, "v2ray/index.html").outputStream().use { out ->
                it.copyTo(out)
            }
        }

        cacheFile.delete()
        coreVersionFile.writeText(v2rayVersion)
    }

}