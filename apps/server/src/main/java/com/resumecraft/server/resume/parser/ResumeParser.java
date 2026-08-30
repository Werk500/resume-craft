package com.resumecraft.server.resume.parser;

import java.io.IOException;
import java.io.InputStream;

/**
 * 简历解析器接口。
 * 每种文件格式实现一个（PDF / Word / 图片），
 * ResumeService 通过 supports() 自动路由到对应实现。
 */
public interface ResumeParser {

    /** 是否支持该文件类型（小写扩展名，如 pdf / docx / png） */
    boolean supports(String fileType);

    /** 从输入流中提取简历纯文本 */
    String parse(InputStream inputStream) throws IOException;
}
