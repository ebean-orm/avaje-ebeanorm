package com.avaje.ebeaninternal.server.grammer;

import com.avaje.ebeaninternal.api.SpiQuery;
import com.avaje.ebeaninternal.server.grammer.antlr.EQLLexer;
import com.avaje.ebeaninternal.server.grammer.antlr.EQLParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

/**
 * Parse EQL query language applying it to an ORM query object.
 */
public class EqlParser {

  private static final ErrorListener errorListener = new ErrorListener();

  /**
   * Parse the raw EQL query and apply it to the supplied query.
   */
  public static <T> void parse(String raw, SpiQuery<T> query) {

    EQLLexer lexer = new EQLLexer(new ANTLRInputStream(raw));
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    EQLParser parser = new EQLParser(tokens);
    parser.addErrorListener(errorListener);
    EQLParser.Select_statementContext context = parser.select_statement();

    EqlAdapter<T> adapter = new EqlAdapter<>(query);

    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(adapter, context);

    query.simplifyExpressions();
  }

  static class ErrorListener extends BaseErrorListener {

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {

      String reportMsg = "line " + line + ":" + charPositionInLine + " " + msg;
      throw new IllegalArgumentException(reportMsg);
    }
  }
}
