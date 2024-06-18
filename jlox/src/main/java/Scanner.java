import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scanner {
    private static final Map<String, Token.Type> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("and", Token.Type.AND);
        keywords.put("class", Token.Type.CLASS);
        keywords.put("else", Token.Type.ELSE);
        keywords.put("false", Token.Type.FALSE);
        keywords.put("for", Token.Type.FOR);
        keywords.put("fun", Token.Type.FUN);
        keywords.put("if", Token.Type.IF);
        keywords.put("nil", Token.Type.NIL);
        keywords.put("or", Token.Type.OR);
        keywords.put("print", Token.Type.PRINT);
        keywords.put("return", Token.Type.RETURN);
        keywords.put("super", Token.Type.SUPER);
        keywords.put("this", Token.Type.THIS);
        keywords.put("true", Token.Type.TRUE);
        keywords.put("var", Token.Type.VAR);
        keywords.put("while", Token.Type.WHILE);
    }

    private final String source;
    private final List<Token> tokens;
    private int start;
    private int current;
    private int line;

    public Scanner(String source) {
        this.source = source;
        this.tokens = new ArrayList<>();
        start = 0;
        current = 0;
        line = 1;
    }

    List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        tokens.add(new Token(Token.Type.EOF, "", null, line));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(':
                addToken(Token.Type.LEFT_PAREN);
                break;
            case ')':
                addToken(Token.Type.RIGHT_PAREN);
                break;
            case '{':
                addToken(Token.Type.LEFT_BRACE);
                break;
            case '}':
                addToken(Token.Type.RIGHT_BRACE);
                break;
            case ',':
                addToken(Token.Type.COMMA);
                break;
            case '.':
                addToken(Token.Type.DOT);
                break;
            case '-':
                addToken(Token.Type.MINUS);
                break;
            case '+':
                addToken(Token.Type.PLUS);
                break;
            case ';':
                addToken(Token.Type.SEMICOLON);
                break;
            case '*':
                addToken(Token.Type.STAR);
                break;
            case '!':
                addToken(match('=') ? Token.Type.BANG_EQUAL : Token.Type.BANG);
                break;
            case '=':
                addToken(match('=') ? Token.Type.EQUAL_EQUAL : Token.Type.EQUAL);
                break;
            case '<':
                addToken(match('=') ? Token.Type.LESS_EQUAL : Token.Type.LESS);
                break;
            case '>':
                addToken(match('=') ? Token.Type.GREATER_EQUAL : Token.Type.GREATER);
                break;
            case '/':
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else {
                    addToken(Token.Type.SLASH);
                }
                break;
            case ' ':
            case '\r':
            case '\t':
                break;
            case '\n':
                line++;
                break;
            case '"':
                string();
                break;
            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    Lox.error(line, "Unexpected character.");
                }
        }
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private char advance() {
        return source.charAt(current++);
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) == expected) {
            advance();
            return true;
        }
        return false;
    }

    private void addToken(Token.Type type) {
        addToken(type, null);
    }

    private void addToken(Token.Type type, Object literal) {
        String lexeme = source.substring(start, current);
        tokens.add(new Token(type, lexeme, literal, line));
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }

        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }

        // Closing ".
        advance();

        // Trim surrounding quotes.
        String value = source.substring(start + 1, current - 1);
        addToken(Token.Type.STRING, value);
    }

    private void number() {
        while (isDigit(peek())) advance();

        if (peek() == '.' && isDigit(peekNext())) {
            // Consume ".".
            advance();

            while (isDigit(peek())) advance();
        }

        double value = Double.parseDouble(source.substring(start, current));
        addToken(Token.Type.NUMBER, value);
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        String text = source.substring(start, current);
        Token.Type type = keywords.get(text);
        if (type == null) type = Token.Type.IDENTIFIER;
        addToken(type);
    }
}
