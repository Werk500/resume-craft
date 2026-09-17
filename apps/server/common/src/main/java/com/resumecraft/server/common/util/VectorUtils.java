package com.resumecraft.server.common.util;
/**
 * 向量工具：余弦相似度、分数映射、pgvector 字面量序列化。
 */
public final class VectorUtils {

    /** 余弦值 → 0~100 分的线性拉伸下界 */
    private static final double COSINE_FLOOR = 0.30;
    /** 余弦值 → 0~100 分的线性拉伸上界 */
    private static final double COSINE_CEIL = 0.90;

    private VectorUtils() {}

    /**
     * 余弦相似度。两个向量长度必须一致。
     *
     * 注意：这里不做预归一化。百炼返回的向量已是单位向量，
     * 但保留除法可保证输入未归一化时结果依然正确（只是多一次开方）。
     */
    public static double cosine(float[] a, float[] b){
        if(a == null || b==null || a.length!=b.length ||a.length ==0){
            return 0.0;
        }

        double dot = 0.0,normA = 0.0,normB = 0.0;
        for(int i=0; i<a.length; i++){
            dot += (double) a[i]*b[i];// 累加点积
            normA += (double) a[i]*a[i];// 累加 a 的平方
            normB += (double) b[i]*b[i];// 累加 b 的平方
        }

        if(normA<=0 || normB<=0) return 0.0;
        return dot/(Math.sqrt(normA)*Math.sqrt(normB));
    }

    /**
     * 余弦值映射到 0~100 分。
     *
     * 为什么不直接乘 100：真实文本对的余弦值通常落在 0.3~0.9，
     * 直接乘 100 会让所有分数挤在 50 分以下，看起来像 bug。
     */
    public static double normalizeToScore(double cosine) {
        double score = (cosine - COSINE_FLOOR) / (COSINE_CEIL - COSINE_FLOOR) * 100.0;
        return Math.max(0.0, Math.min(100.0, score));
    }

    /**
     * float[] → pgvector 字面量，形如 [0.1,0.2,0.3]。
     *
     * 关键：不要做四舍五入截断。pgvector 内部按 float32 存储，
     * 序列化时保留足量精度，否则每次写入都会累积误差。
     */
    public static String toPgVector(float[] vector){
        if(vector==null || vector.length==0){
            throw new IllegalArgumentException("向量不能为空");
        }

        StringBuilder sb = new StringBuilder(vector.length * 10 + 2);
        sb.append('[');
        for(int i=0; i<vector.length; i++){
            if(i>0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    /**
     * pgvector 返回的字符串 → float[]。
     *
     * 注意：pgvector 的 JDBC 读取结果是形如 "[0.1,0.2]" 的字符串，
     * PostgreSQL 驱动并不把它映射成 Java 数组，必须自己解析。
     */
    public static float[] fromPgVector(String text) {
        if (text == null || text.isBlank()) return null;
        String s = text.trim();
        //去掉首尾的 [ 和 ]，剩下 "0.1,0.2,0.3"。
        if(s.startsWith("[")) s = s.substring(1);
        if(s.endsWith("]")) s = s.substring(0, s.length()-1);

        if (s.isBlank())  return null;

        String[] parts = s.split(",");
        float[] result = new float[parts.length];
        for(int i=0; i<parts.length; i++){
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }


}
