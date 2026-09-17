package com.nyberg.directory.intel;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Same classifier as IAM {@code PublicIps}: only public addresses hit MaxMind. */
public final class PublicIps {

    private PublicIps() {}

    public static String normalizeOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = strip(raw);
        if (!looksLikeIp(s)) {
            return null;
        }
        InetAddress addr;
        try {
            addr = InetAddress.getByName(s);
        } catch (UnknownHostException e) {
            return null;
        }
        addr = unwrapIpv4Mapped(addr);
        if (!isPublic(addr)) {
            return null;
        }
        return addr.getHostAddress();
    }

    static boolean isPublic(InetAddress addr) {
        if (addr.isAnyLocalAddress()
                || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isMulticastAddress()
                || addr.isSiteLocalAddress()) {
            return false;
        }
        byte[] b = addr.getAddress();
        if (addr instanceof Inet4Address && b.length == 4) {
            int o0 = b[0] & 0xff;
            int o1 = b[1] & 0xff;
            if (o0 == 100 && o1 >= 64 && o1 <= 127) {
                return false;
            }
            if (o0 == 0) {
                return false;
            }
        }
        if (addr instanceof Inet6Address && b.length == 16) {
            if ((b[0] & 0xfe) == 0xfc) {
                return false;
            }
        }
        return true;
    }

    private static InetAddress unwrapIpv4Mapped(InetAddress addr) {
        if (!(addr instanceof Inet6Address) || addr.getAddress().length != 16) {
            return addr;
        }
        byte[] b = addr.getAddress();
        boolean mapped = true;
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                mapped = false;
                break;
            }
        }
        if (!mapped || b[10] != (byte) 0xff || b[11] != (byte) 0xff) {
            return addr;
        }
        try {
            return InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]});
        } catch (UnknownHostException e) {
            return addr;
        }
    }

    private static String strip(String raw) {
        String s = raw.trim();
        if (s.startsWith("[") && s.contains("]")) {
            s = s.substring(1, s.indexOf(']'));
        }
        int pct = s.indexOf('%');
        if (pct >= 0) {
            s = s.substring(0, pct);
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        return s.trim();
    }

    private static boolean looksLikeIp(String s) {
        if (s.contains(":")) {
            return true;
        }
        int dots = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                dots++;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        return dots == 3;
    }
}
