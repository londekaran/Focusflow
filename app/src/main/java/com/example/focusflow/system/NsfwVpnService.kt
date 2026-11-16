package com.example.focusflow.system

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.example.focusflow.data.ServiceLocator
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/** Decides which host names are adult content. Pure Kotlin. */
object NsfwFilter {
    private val tlds = listOf(".xxx", ".porn", ".adult", ".sex", ".sexy")
    private val words = listOf(
        "porn", "xxx", "xvideos", "xnxx", "xhamster", "hentai", "redtube", "youporn", "brazzers", "spankbang",
        "chaturbate", "stripchat", "onlyfans", "nsfw", "rule34", "e621", "livejasmin", "bangbros", "camsoda",
        "erome", "fapello", "motherless", "eporner", "tnaflix", "nhentai", "xtube", "thothub", "fansly"
    )
    fun isBlocked(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return tlds.any { h.endsWith(it) } || words.any { h.contains(it) }
    }
}

/**
 * Local DNS filter. Only DNS lookups are routed into this VPN; blocked names get "no such domain",
 * all others are forwarded to a family-safe resolver. Works for every browser and app.
 */
class NsfwVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private val pool = Executors.newFixedThreadPool(4)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stop(); stopSelf(); return START_NOT_STICKY }
        if (running) return START_STICKY
        tun = Builder().setSession("AMU adult-content blocker")
            .addAddress("10.111.222.1", 32).addDnsServer(FAKE_DNS).addRoute(FAKE_DNS, 32)
            .setBlocking(true).establish()
        if (tun == null) { stopSelf(); return START_NOT_STICKY }
        running = true
        Thread { loop(tun!!) }.start()
        return START_STICKY
    }

    private fun stop() { running = false; tun?.close(); tun = null }
    override fun onDestroy() { stop(); pool.shutdownNow(); super.onDestroy() }
    override fun onRevoke() { ServiceLocator.settings(this).nsfwBlock = false; stop(); super.onRevoke() }

    private fun loop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (running) {
                val n = input.read(buf)
                if (n <= 0) continue
                val pkt = buf.copyOf(n)
                pool.execute { try { handle(pkt, output) } catch (_: Exception) { } }
            }
        } catch (_: Exception) { }
    }

    private fun handle(p: ByteArray, out: FileOutputStream) {
        if (p.size < 28 || (p[0].toInt() shr 4) != 4 || p[9].toInt() != 17) return
        val ihl = (p[0].toInt() and 15) * 4
        val dstPort = ((p[ihl + 2].toInt() and 255) shl 8) or (p[ihl + 3].toInt() and 255)
        if (dstPort != 53) return
        val dns = p.copyOfRange(ihl + 8, p.size)
        val host = qname(dns) ?: return
        val reply: ByteArray = if (NsfwFilter.isBlocked(host)) {
            val c = ServiceLocator.settings(this); c.nsfwBlockedCount = c.nsfwBlockedCount + 1
            dns.copyOf().also { it[2] = (it[2].toInt() or 0x80).toByte(); it[3] = 0x83.toByte() }   // response, NXDOMAIN
        } else {
            DatagramSocket().use { s ->
                protect(s); s.soTimeout = 4000
                val up = InetAddress.getByName(UPSTREAM)   // Cloudflare family DNS also blocks adult sites
                s.send(DatagramPacket(dns, dns.size, up, 53))
                val r = ByteArray(4096); val rp = DatagramPacket(r, r.size)
                s.receive(rp); r.copyOf(rp.length)
            }
        }
        out.write(wrap(p, ihl, reply))
    }

    /** First question name of a DNS query, like "www.example.com". */
    private fun qname(d: ByteArray): String? {
        var i = 12; val sb = StringBuilder()
        while (i < d.size) {
            val l = d[i].toInt() and 255
            if (l == 0) break
            if (l > 63 || i + l >= d.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(d, i + 1, l, Charsets.US_ASCII)); i += l + 1
        }
        return sb.toString().ifEmpty { null }
    }

    /** Builds an IPv4+UDP packet answering [q] (addresses and ports swapped). */
    private fun wrap(q: ByteArray, ihl: Int, payload: ByteArray): ByteArray {
        val total = 20 + 8 + payload.size
        val o = ByteArray(total)
        o[0] = 0x45; o[2] = (total shr 8).toByte(); o[3] = total.toByte(); o[8] = 64; o[9] = 17
        System.arraycopy(q, 16, o, 12, 4); System.arraycopy(q, 12, o, 16, 4)   // src <- old dst, dst <- old src
        var sum = 0
        for (i in 0 until 20 step 2) sum += ((o[i].toInt() and 255) shl 8) or (o[i + 1].toInt() and 255)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val ck = sum.inv() and 0xFFFF
        o[10] = (ck shr 8).toByte(); o[11] = ck.toByte()
        o[20] = 0; o[21] = 53; o[22] = q[ihl]; o[23] = q[ihl + 1]            // ports swapped
        val ul = 8 + payload.size; o[24] = (ul shr 8).toByte(); o[25] = ul.toByte()   // UDP checksum 0 = none
        System.arraycopy(payload, 0, o, 28, payload.size)
        return o
    }

    companion object {
        private const val FAKE_DNS = "10.111.222.2"
        private const val UPSTREAM = "1.1.1.3"
        const val ACTION_STOP = "stop"
        fun start(ctx: Context) { ctx.startService(Intent(ctx, NsfwVpnService::class.java)) }
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, NsfwVpnService::class.java).setAction(ACTION_STOP)) }
    }
}
