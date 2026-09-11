package com.resumecraft.server.resume.parser;

import com.resumecraft.server.resume.parser.dto.OcrParseResult;

import java.io.IOException;
import java.io.InputStream;

public interface ConfidenceParser extends  ResumeParser {

    OcrParseResult parseWithBlocks(InputStream in) throws IOException;
}
