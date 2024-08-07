import java.util.ArrayList;
import java.util.List;

public class Parser {
    private static class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;
    private int current;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        current = 0;
    }

    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(Token.Type.FUN)) return function("function");
            if (match(Token.Type.VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt function(String kind) {
        Token name = consume(Token.Type.IDENTIFIER, "Expect " + kind + " name.");
        consume(Token.Type.LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<Token> params = new ArrayList<>();
        if (!check(Token.Type.RIGHT_PAREN)) {
            do {
                if (params.size() >= 255) {
                    error(peek(), "Can't have more than 255 parameters.");
                }

                params.add(consume(Token.Type.IDENTIFIER,
                        "Expect parameter name."));
            } while (match(Token.Type.COMMA));
        }
        consume(Token.Type.RIGHT_PAREN, "Expect ')' after parameters.");

        consume(Token.Type.LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, params, body);
    }

    private Stmt varDeclaration() {
        Token name = consume(Token.Type.IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(Token.Type.EQUAL)) initializer = expression();

        consume(Token.Type.SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt statement() {
        if (match(Token.Type.FOR)) return forStatement();
        if (match(Token.Type.IF)) return ifStatement();
        if (match(Token.Type.PRINT)) return printStatement();
        if (match(Token.Type.RETURN)) return returnStatement();
        if (match(Token.Type.WHILE)) return whileStatement();
        if (match(Token.Type.LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    private Stmt forStatement() {
        consume(Token.Type.LEFT_PAREN, "Expect '(' after 'for'.");

        Stmt initializer;
        if (match(Token.Type.SEMICOLON)) {
            initializer = null;
        } else if (match(Token.Type.VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(Token.Type.SEMICOLON)) {
            condition = expression();
        }
        consume(Token.Type.SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(Token.Type.SEMICOLON)) {
            increment = expression();
        }
        consume(Token.Type.RIGHT_PAREN, "Expect ')' after for clauses.");
        Stmt body = statement();

        if (increment != null) {
            body = new Stmt.Block(List.of(body, new Stmt.Expression(increment)));
        }

        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        if (initializer != null) {
            body = new Stmt.Block(List.of(initializer, body));
        }

        return body;
    }

    private Stmt ifStatement() {
        consume(Token.Type.LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(Token.Type.RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(Token.Type.ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt printStatement() {
        Expr expr = expression();
        consume(Token.Type.SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(expr);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(Token.Type.SEMICOLON)) {
            value = expression();
        }

        consume(Token.Type.SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private Stmt whileStatement() {
        consume(Token.Type.LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(Token.Type.RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(Token.Type.RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(Token.Type.RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(Token.Type.SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = or();

        if (match(Token.Type.EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();

        if (match(Token.Type.OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        if (match(Token.Type.AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();

        while (match(Token.Type.EQUAL_EQUAL, Token.Type.BANG_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = term();

        while (match(Token.Type.GREATER, Token.Type.GREATER_EQUAL,
                Token.Type.LESS, Token.Type.LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(Token.Type.PLUS, Token.Type.MINUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match(Token.Type.STAR, Token.Type.SLASH)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(Token.Type.BANG, Token.Type.MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }

    private Expr call() {
        Expr expr = primary();

        while (match(Token.Type.LEFT_PAREN)) {
            expr = finishCall(expr);
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(Token.Type.RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255) {
                    error(peek(), "Can't have more than 255 arguments.");
                }
                arguments.add(expression());
            } while (match(Token.Type.COMMA));
        }

        Token paren = consume(Token.Type.RIGHT_PAREN,
                "Expect ')' after arguments.");

        return new Expr.Call(callee, paren, arguments);
    }

    private Expr primary() {
        if (match(Token.Type.TRUE)) return new Expr.Literal(true);
        if (match(Token.Type.FALSE)) return new Expr.Literal(false);
        if (match(Token.Type.NIL)) return new Expr.Literal(null);

        if (match(Token.Type.NUMBER, Token.Type.STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(Token.Type.IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(Token.Type.LEFT_PAREN)) {
            Expr expr = expression();
            consume(Token.Type.RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    private boolean match(Token.Type... types) {
        for (Token.Type type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private boolean check(Token.Type type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == Token.Type.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private Token consume(Token.Type type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == Token.Type.SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }
}
