package com.decisionassistant.guidance;

import com.decisionassistant.common.InvalidDecisionException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;

@Component
public class PublicUrlPolicy {
    public String validate(String input) {
        try {
            var uri = URI.create(input == null ? "" : input.trim());
            var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            var host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
            if (!(scheme.equals("http") || scheme.equals("https")) || host.isBlank() || uri.getUserInfo() != null
                    || host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                    || privateLiteral(host)) {
                throw new IllegalArgumentException();
            }
            return uri.toASCIIString();
        } catch (RuntimeException exception) {
            throw new InvalidDecisionException(List.of("공개 HTTP(S) URL만 추가할 수 있습니다."));
        }
    }

    private boolean privateLiteral(String host) {
        if (host.contains(":")) {
            try {
                // Only IPv6 literals: never resolve a user-provided DNS name on the server.
                var address = java.net.InetAddress.getByName(host);
                var bytes = address.getAddress();
                return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc);
            } catch (java.net.UnknownHostException exception) { return true; }
        }
        if (host.matches("[0-9.]+") && !host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return true;
        if (host.startsWith("0.") || host.startsWith("10.") || host.startsWith("127.")
                || host.startsWith("169.254.") || host.startsWith("192.168.")) return true;
        if (!host.startsWith("172.")) return false;
        var parts = host.split("\\.");
        if (parts.length < 2) return false;
        try { var second = Integer.parseInt(parts[1]); return second >= 16 && second <= 31; }
        catch (NumberFormatException ignored) { return false; }
    }
}
