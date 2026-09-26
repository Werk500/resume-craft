package com.resumecraft.server.job.agent;

import java.util.UUID;

public final class AgentMemoryId {
    private static final char SEP = ':';

    private AgentMemoryId() {}

    /**
     * 归一成 {userId}:{sessionId}。幂等——前端会把上一轮返回的 memoryId 原样传回来，
     * 所以要先把可能已存在的 "数字:" 前缀剥掉再拼，
     * 这样客户端伪造 "999:xxx" 也没用：剥掉后按真实 userId 重拼。
     */
    public static String normalize(Long userId, String incomingSession) {
        String session = (incomingSession == null || incomingSession.isBlank())
                ? UUID.randomUUID().toString()
                : sessionOf(incomingSession);
        return userId + String.valueOf(SEP) + session;
    }

    /** 从 memoryId 里解出 userId（工具用） */
    public static Long userIdOf(String memoryId) {
        // 解析失败抛 IllegalArgumentException，不要静默返回 null

        if(memoryId == null){
            throw new IllegalArgumentException("memoryId must not be null");
        }

        int idx = memoryId.indexOf(SEP);
        if(idx <= 0){
            throw new IllegalArgumentException("invalid memoryId, no userId prefix: " + memoryId);
        }

        String prefix = memoryId.substring(0, idx);
        try {
            return Long.valueOf(prefix);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid memoryId, userId is not a number: " + memoryId, e);
        }
    }

    /** 剥离可能存在的 {userId}: 前缀 */
    public static String sessionOf(String raw) {
        // 首个 ':' 之前全是数字才认为是前缀
        if(raw == null){
            throw new IllegalArgumentException("session must not be null");
        }
        int idx = raw.indexOf(SEP);
        if(idx <= 0){
            return raw;
        }

        String prefix = raw.substring(0, idx);
        if(!isAllDigits(prefix)){
            return raw;
        }

        return raw.substring(idx+1);
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;// 遇到一个不是数字的，立刻返回 false
            }
        }
        return true;
    }
}
