/*
 * Copyright 2009-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.eclipse.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.groovy.eclipse.core.ISourceBuffer;
import org.codehaus.groovy.eclipse.core.impl.ReverseSourceBuffer;

/**
 * Token stream used by the ExpressionFinder parser.
 *
 * @author empovazan
 */
public class TokenStream {
    private static final Token TOKEN_EOF = new Token(Token.Type.EOF, -1, -1, null);

    private ISourceBuffer buffer;

    private int offset;

    private char ch;

    private Token last;

    private Token next = null;

    public TokenStream(ISourceBuffer buffer, int offset) {
        this.buffer = buffer;
        this.offset = offset;
        this.ch = buffer.charAt(offset);
    }

    /**
     * @return The next token in the stream.
     * @throws TokenStreamException
     */
    public Token peek() throws TokenStreamException {
        int offset = this.offset;
        char ch = this.ch;
        Token last = this.last;
        Token next = this.next;

        Token ret = next();

        this.offset = offset;
        this.ch = ch;
        this.last = last;
        this.next = next;

        return ret;
    }

    public char getCurrentChar() {
        return ch;
    }

    /**
     * @return The next token in the stream.
     */
    public Token next() throws TokenStreamException {
        if (next != null) {
            last = next;
            next = null;
            return last;
        }
        if (offset == -1) {
            return TOKEN_EOF;
        }

        if (Character.isWhitespace(ch)) {
            skipWhite();
            if (offset == -1) {
                return TOKEN_EOF;
            }
        }

        if (isLineBreakChar()) {
            last = skipLineBreak();
            next = skipLineComment();
            return last;
        }

        if (ch == '/' && la(1) == '*') {
            last = scanBlockComment();
            return last;
        }

        if (Character.isJavaIdentifierPart(ch)) {
            last = scanIdent();
        } else {
            switch (ch) {
            case '.':
                last = scanDot();
                break;
            case '}':
                last = scanPair('{', '}', Token.Type.BRACE_BLOCK);
                break;
            case ')':
                last = scanPair('(', ')', Token.Type.PAREN_BLOCK);
                break;
            case ']':
                last = scanPair('[', ']', Token.Type.BRACK_BLOCK);
                break;
            case '\'':
                last = scanQuote('\'');
                break;
            case '"':
                last = scanQuote('"');
                break;
            case '@':
                nextChar();
                if (ch == '.') {
                    nextChar();
                    last = new Token(Token.Type.FIELD_ACCESS, offset + 1, offset + 3, buffer.subSequence(offset + 1, offset + 3).toString());
                } else {
                    last = new Token(Token.Type.IDENT, offset + 1, offset + 2, buffer.subSequence(offset + 1, offset + 2).toString());
                }
                break;
            case '&':
                nextChar();
                if (ch == '.') {
                    nextChar();
                    last = new Token(Token.Type.METHOD_POINTER, offset + 1, offset + 3, buffer.subSequence(offset + 1, offset + 3).toString());
                }
                break;
            case ';':
                nextChar();
                last = new Token(Token.Type.SEMI, offset + 1, offset + 2, buffer.subSequence(offset + 1, offset + 2).toString());
                break;
            default:
                throw new TokenStreamException(ch);
            }
        }
        return last;
    }

    private Token scanDot() {
        nextChar();
        if (offset == -1) {
            return TOKEN_EOF;
        }
        if (ch == '.') {
            nextChar();
            return new Token(Token.Type.DOUBLE_DOT, offset + 1, offset + 3, buffer.subSequence(offset + 1, offset + 3).toString());
        } if (ch == '?')  {
            nextChar();
            return new Token(Token.Type.SAFE_DEREF, offset + 1, offset + 3, buffer.subSequence(offset + 1, offset + 3).toString());
        } if (ch == '*') {
            nextChar();
            return new Token(Token.Type.SPREAD, offset + 1, offset + 3, buffer.subSequence(offset + 1, offset + 3).toString());
        }
        return new Token(Token.Type.DOT, offset + 1, offset + 2, buffer.subSequence(offset + 1, offset + 2).toString());
    }

    private Token skipLineBreak() {
        int endOffset = offset + 1;
        char firstChar = ch;
        nextChar();
        if (offset != -1 && isLineBreakChar()) {
            char secondChar = ch;

            nextChar();
            return new Token(Token.Type.LINE_BREAK, offset + 1, endOffset, new String(new char[]{firstChar, secondChar}));
        }
        return new Token(Token.Type.LINE_BREAK, offset+ 1, endOffset, new String(new char[]{firstChar}));
    }

    private boolean isLineBreakChar() {
        return ch == '\n' || ch == '\r';
    }

    /**
     * @return The last token retrieved using {@link #peek()}
     */
    public Token last() {
        return last;
    }

    private void nextChar() {
        if (offset == -1)
            throw new IllegalStateException("tried to get next char after eof");
        if (offset == 0) {
            offset = -1;
        } else {
            ch = buffer.charAt(--offset);
        }
    }

    /**
     * Scans closing and opening pairs, ignoring nested pairs.
     */
    private Token scanPair(char open, char close, Token.Type type) throws TokenStreamException {
        int endOffset = offset + 1;
        int pairCount = 1;
        while (pairCount > 0 && offset > 0) {
            ch = buffer.charAt(--offset);
            if (ch == open) {
                pairCount -= 1;
            } else if (ch == close) {
                pairCount += 1;
            }
        }
        if (offset != 0) {
            ch = buffer.charAt(--offset);
        } else {
            offset = -1;
            if (pairCount != 0) {
                throw new TokenStreamException("Unclosed pair at EOF");
            }
        }

        return new Token(type, offset + 1, endOffset, buffer.subSequence(offset + 1, endOffset).toString());
    }

    private Token scanIdent() {
        int endOffset = offset + 1;
        do {
            nextChar();
        } while (offset > -1 && Character.isJavaIdentifierPart(ch));

        return new Token(Token.Type.IDENT, offset + 1, endOffset, buffer.subSequence(offset + 1, endOffset).toString());
    }

    private Token scanQuote(char quote) throws TokenStreamException {
        Pattern singleQuote;
        Pattern tripleQuote;
        if (quote == '\'') {
            singleQuote = Pattern.compile("^\'.*\'");
            tripleQuote = Pattern.compile("^\'\'\'.*\'\'\'");
        } else {
            singleQuote = Pattern.compile("^\".*\"");
            tripleQuote = Pattern.compile("^\"\"\".*\"\"\"");
        }

        Token token = matchQuote(tripleQuote);
        if (token != null) {
            return token;
        }

        token = matchQuote(singleQuote);
        if (token != null) {
            return token;
        }

        throw new TokenStreamException(
                "Could not close quoted string, end offset = " + offset);
    }

    private Token matchQuote(Pattern quotePattern) {
        ISourceBuffer matchBuffer = new ReverseSourceBuffer(this.buffer, offset);
        Matcher matcher = quotePattern.matcher(matchBuffer);
        if (matcher.find()) {
            String match = matcher.group(0);
            int endOffset = offset + 1;
            int startOffset = offset - match.length() + 1;
            offset = startOffset;
            if (offset == 0) {
                offset = -1;
            }
            if (offset != -1) {
                offset -= 1;
                ch = buffer.charAt(offset);
            }
            return new Token(Token.Type.QUOTED_STRING, startOffset, endOffset, match);
        }
        return null;
    }

    private void skipWhite() {
        if (isLineBreakChar())
            return;
        do {
            nextChar();
        } while (Character.isWhitespace(ch) && !isLineBreakChar() && offset > -1);
    }

    private Token skipLineComment() {
        ISourceBuffer matchBuffer = new ReverseSourceBuffer(this.buffer, offset);
        Pattern pattern = Pattern.compile(".*//");
        Matcher matcher = pattern.matcher(matchBuffer);
        if (matcher.find() && matcher.start()==0) {
            String match = matcher.group(0);
            int endOffset = offset + 1;
            int startOffset = offset - match.length() + 1;
            offset = startOffset;
            if (offset != 0) {
                ch = buffer.charAt(--offset);
            } else {
                ch = buffer.charAt(offset--);
            }
            return new Token(Token.Type.LINE_COMMENT, startOffset, endOffset, match);
        }
        // } else {
        // ch = buffer.charAt(--offset);
        // if (ch == '\r' && offset != 0) {
        // ch = buffer.charAt(--offset);
        // } else if (offset == 0) {
        // offset = -1;
        // return TOKEN_EOF;
        // }
        // }
        return null;
    }

    private Token scanBlockComment() {
        ISourceBuffer matchBuffer = new ReverseSourceBuffer(this.buffer, offset);
        Pattern pattern = Pattern.compile("(?s)/\\*.*\\*/");
        Matcher matcher = pattern.matcher(matchBuffer);
        if (matcher.find()) {
            String match = matcher.group(0);
            int endOffset = offset + 1;
            int startOffset = offset - match.length() + 1;
            offset = startOffset;
            if (offset != 0) {
                ch = buffer.charAt(--offset);
            } else {
                ch = buffer.charAt(offset--);
            }
            return new Token(Token.Type.BLOCK_COMMENT, startOffset, endOffset, match);
        } else {
            ch = buffer.charAt(--offset);
        }
        return null;
    }

    private char la(int index) {
        if (offset - index >= 0) {
            return buffer.charAt(offset - index);
        }
        return 0;
    }
}
