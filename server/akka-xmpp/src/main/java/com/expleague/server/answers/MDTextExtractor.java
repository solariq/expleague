package com.expleague.server.answers;

import com.spbsu.commons.io.StreamTools;
import com.vladsch.flexmark.parser.Parser;
import org.apache.jackrabbit.extractor.TextExtractor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Experts League
 * Created by solar on 18.04.17.
 */
public class MDTextExtractor implements TextExtractor {
  final Parser.Builder builder = Parser.builder();

  public MDTextExtractor() {
    System.out.println();
  }

  @Override
  public String[] getContentTypes() {
    return new String[]{"text/markdown", "text/x-markdown"};
  }

  @Override
  public Reader extractText(InputStream stream, String type, String encoding) throws IOException {
    final Parser parser = builder.build();
    final com.vladsch.flexmark.ast.Node node = parser.parseReader(new InputStreamReader(stream, StreamTools.UTF));
    return new InputStreamReader(stream, StreamTools.UTF);
  }
}
