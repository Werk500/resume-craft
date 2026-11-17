package com.resumecraft.server.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 解析限流使用的客户端 IP。
 *
 * <p>只有请求的 TCP 对端是可信代理时，才读取 X-Forwarded-For / X-Real-IP。
 * 这样可以避免客户端直接伪造代理头绕过限流，也能避免 Next.js、Nginx
 * 等反向代理导致所有匿名用户共用代理自身 IP 的限流桶。
 */
@Component
public class ClientIpResolver {

    private static final String DEFAULT_TRUSTED_PROXY_CIDRS =
            "127.0.0.1/32,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16";


    private final List<CidrRange> trustedProxies;

    public ClientIpResolver(@Value("${app.gateway.trusted-proxy-cidrs:"
            + DEFAULT_TRUSTED_PROXY_CIDRS+"}") String trustedProxyCidrs) {
        this.trustedProxies = parseCidrs(trustedProxyCidrs);
    }


    public String resolveClientIp(ServerWebExchange exchange) {

        ServerHttpRequest request = exchange.getRequest();

        //拿到上一跳真实地址
        String peerIp = extractIp(request.getRemoteAddress());

        if(!isTrustedProxy(peerIp)) {
            return peerIp;
        }

        //X-Forwarded-For (XFF)
        String forwardedIp = resolveForwardedFor(
                request.getHeaders().getFirst("X-Forwarded-For")
        );
        if (forwardedIp != null) {
            return forwardedIp;
        }

        //X-Real-IP
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (isValidIp(realIp)) {
            return normalizeIp(realIp);
        }

        return peerIp;

    }

    private record CidrRange(byte[] network,int prefixLength) {
        private boolean matches(InetAddress address) {
            byte[] candidate = address.getAddress();

            if (candidate.length != network.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }

            if (remainingBits == 0) {
                return true;
            }

            int mask = 0xff << (8 - remainingBits);
            return (candidate[fullBytes] & mask)
                    == (network[fullBytes] & mask);
        }
    }

    /**
     * 从右往左解析X-Forwarded-For
     * @param forwardedFor
     * @return
     */
    private String resolveForwardedFor(String forwardedFor) {
        if (!StringUtils.hasText(forwardedFor)) {
            return null;
        }

        List<String> hops = Arrays.stream(forwardedFor.split(","))
                .map(String::trim)
                .filter(this::isValidIp)
                .toList();

        if (hops.isEmpty()) {
            return null;
        }

        for (int i = hops.size() - 1; i >= 0; i--) {
            String hop = normalizeIp(hops.get(i));
            if (!isTrustedProxy(hop)) {
                return hop;
            }
        }

        return normalizeIp(hops.get(0));
    }

    /**
     * 归一化
     * @param value
     * @return
     */
    private String normalizeIp(String value) {
        String ip = value.trim();

        if (ip.startsWith("[") && ip.endsWith("]")) {
            ip = ip.substring(1, ip.length() - 1);
        }

        if (ip.startsWith("::ffff:")) {
            ip = ip.substring("::ffff:".length());
        }

        return ip;
    }

    /**
     * ip合法性判断
     * @param value
     * @return
     */
    private boolean isValidIp(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }

        String ip = normalizeIp(value.trim());

        if ("unknown".equalsIgnoreCase(ip)) {
            return false;
        }

        try {
            if (!ip.contains(":") && !ip.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) {
                return false;
            }
            InetAddress.getByName(ip);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String extractIp(InetSocketAddress address) {
        if (address == null || address.getAddress() == null) {
            return "unknown";
        }
        return normalizeIp(address.getAddress().getHostAddress());
    }

    private boolean isTrustedProxy(String ip) {

        //校验ip合法性
        if (!isValidIp(ip)) {
            return false;
        }

        String normalized = normalizeIp(ip);
        InetAddress clientAddress;
        try {
            // 确保 isValidIp 是强正则，防止 getByName 触发 DNS 解析
            clientAddress = InetAddress.getByName(normalized);
        } catch (Exception e) {
            // 解析失败说明传入的就不是个合法 IP，绝对不信任，直接返回 false，绝不抛异常
            return false;
        }

        // 遍历信任列表进行匹配（此时不再有异常风险）
        return trustedProxies.stream()
                .anyMatch(matcher -> matcher.matches(clientAddress));
    }


    private List<CidrRange> parseCidrs(String configuredCidrs) {
        List<CidrRange> cidrs = new ArrayList<>();

        for (String raw : configuredCidrs.split(",")) {
            String value = raw.trim();

            if(value.isBlank()){
                continue;
            }

            String[] parts = value.split("/", 2);

            try{
                //动态计算前缀长度
                InetAddress network = InetAddress.getByName(parts[0]);
                int maxPrefix = network.getAddress().length * 8;

                //默认掩码机制
                int prefix = parts.length == 2
                        ? Integer.parseInt(parts[1])
                        : maxPrefix;

                //越界校验与异常包装
                if (prefix < 0 || prefix > maxPrefix) {
                    throw new IllegalArgumentException(
                            "Invalid CIDR prefix: " + value
                    );
                }

                cidrs.add(new CidrRange(network.getAddress(), prefix));

            }catch (Exception e) {
                throw new IllegalArgumentException(
                        "Invalid trusted proxy CIDR: " + value,
                        e
                );
            }
        }
        return List.copyOf(cidrs);
    }
}
