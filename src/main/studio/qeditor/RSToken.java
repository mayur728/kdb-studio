package studio.qeditor;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import studio.kdb.Config;
import studio.kdb.Config.ThemeEntry;

import java.awt.*;
import java.util.Arrays;

public enum RSToken {

    NULL        (TokenTypes.NULL,                                   Config.getThemeEntry(ThemeEntry.DEFAULT   )),
    SYMBOL      (TokenTypes.DEFAULT_NUM_TOKEN_TYPES,                Config.getThemeEntry(ThemeEntry.SYMBOL    )),
    STRING      (TokenTypes.LITERAL_CHAR,                           Config.getThemeEntry(ThemeEntry.CHARVECTOR)),
    ML_STRING   (TokenTypes.DEFAULT_NUM_TOKEN_TYPES + 1,            Config.getThemeEntry(ThemeEntry.CHARVECTOR)),
    ERROR_STRING(TokenTypes.DEFAULT_NUM_TOKEN_TYPES + 2, Font.BOLD, Config.getThemeEntry(ThemeEntry.ERROR     )),
    IDENTIFIER  (TokenTypes.IDENTIFIER,                             Config.getThemeEntry(ThemeEntry.IDENTIFIER)),
    OPERATOR    (TokenTypes.OPERATOR,                               Config.getThemeEntry(ThemeEntry.OPERATOR  )),
    BRACKET     (TokenTypes.SEPARATOR,                              Config.getThemeEntry(ThemeEntry.BRACKET   )),
    EOL_COMMENT (TokenTypes.COMMENT_EOL, Font.ITALIC,               Config.getThemeEntry(ThemeEntry.EOLCOMMENT)),
    ML_COMMENT  (TokenTypes.COMMENT_MULTILINE, Font.ITALIC,         Config.getThemeEntry(ThemeEntry.EOLCOMMENT)),
    KEYWORD     (TokenTypes.RESERVED_WORD, Font.BOLD,               Config.getThemeEntry(ThemeEntry.KEYWORD   )),
    WHITESPACE  (TokenTypes.WHITESPACE,                             Config.getThemeEntry(ThemeEntry.WHITESPACE)),
    UNKNOWN     (TokenTypes.ERROR_NUMBER_FORMAT, Font.BOLD,         Config.getThemeEntry(ThemeEntry.ERROR     )),
    INTEGER     (TokenTypes.DEFAULT_NUM_TOKEN_TYPES + 3,            Config.getThemeEntry(ThemeEntry.INTEGER   )),
    MINUTE      (TokenTypes.DEFAULT_NUM_TOKEN_TYPES + 4,            Config.getThemeEntry(ThemeEntry.MINUTE    )),
    SECOND      (TokenTypes.DEFAULT_NUM_TOKEN_TYPES + 5,            Config.getThemeEntry(ThemeEntry.SECOND    )),
    TIME        (TokenTypes.DEFAULT_NUM_TOKEN_TYPES + 6,            Config.getThemeEntry(ThemeEntry.TIME      )),
    DATE        (TokenTypes.DEFAULT_NUM_TOKEN_TYPES + 7,            Config.getThemeEntry(ThemeEntry.DATE      )),
    MONTH       (TokenTypes.DEFAULT_NUM_TOKEN_TYPES + 8,            Config.getThemeEntry(ThemeEntry.MONTH     )),
    FLOAT       (TokenTypes.LITERAL_NUMBER_FLOAT,                   Config.getThemeEntry(ThemeEntry.FLOAT     )),
    LONG        (TokenTypes.LITERAL_NUMBER_DECIMAL_INT,             Config.getThemeEntry(ThemeEntry.LONG      )),
    SHORT       (TokenTypes.DEFAULT_NUM_TOKEN_TYPES + 9,            Config.getThemeEntry(ThemeEntry.SHORT     )),
    REAL        (TokenTypes.DEFAULT_NUM_TOKEN_TYPES + 10,           Config.getThemeEntry(ThemeEntry.REAL      )),
    BYTE        (TokenTypes.DEFAULT_NUM_TOKEN_TYPES + 11,           Config.getThemeEntry(ThemeEntry.BYTE      )),
    BOOLEAN     (TokenTypes.LITERAL_BOOLEAN,                        Config.getThemeEntry(ThemeEntry.BOOLEAN   )),
    DATETIME    (TokenTypes.DEFAULT_NUM_TOKEN_TYPES + 12,           Config.getThemeEntry(ThemeEntry.DATETIME  )),
    TIMESTAMP   (TokenTypes.DEFAULT_NUM_TOKEN_TYPES + 13,           Config.getThemeEntry(ThemeEntry.TIMESTAMP )),
    TIMESPAN    (TokenTypes.DEFAULT_NUM_TOKEN_TYPES + 14,           Config.getThemeEntry(ThemeEntry.TIMESPAN  )),
    SYSTEM      (TokenTypes.PREPROCESSOR,                           Config.getThemeEntry(ThemeEntry.SYSTEM    )),
    COMMAND     (TokenTypes.VARIABLE,                               Config.getThemeEntry(ThemeEntry.COMMAND   ));

    public final static int NUM_TOKEN_TYPES = TokenTypes.DEFAULT_NUM_TOKEN_TYPES + 15;

    private static RSToken[] tokenTypesToQToken = new RSToken[NUM_TOKEN_TYPES];
    static {
        Arrays.fill(tokenTypesToQToken, null);
        for (RSToken token: values()) {
            tokenTypesToQToken[token.getTokenType()] = token;
        }
    }

    public static RSToken fromTokenType(int tokenType) {
        RSToken result = tokenTypesToQToken[tokenType];
        if (result == null) throw new IllegalArgumentException(String.format("Token type %d is not defined", tokenType));
        return result;
    }

    public int getTokenType() {
        return tokenType;
    }

    private int tokenType;
    private int fontStyle;
    private String colorTokenName;

    Style getStyle() {
        Font font = Config.getInstance().getFont(Config.FONT_EDITOR);
        if (fontStyle != Font.PLAIN) font = font.deriveFont(fontStyle);
        Color color = Config.getInstance().getColor(colorTokenName);
        return new Style(color, null, font);
    }

    RSToken(int tokenType, int fontStyle, String colorTokenName) {
        this.tokenType = tokenType;
        this.fontStyle = fontStyle;
        this.colorTokenName = colorTokenName;
    }

    RSToken(int tokenType, String colorTokenName) {
        this(tokenType, Font.PLAIN, colorTokenName);
    }

    RSToken(int tokenType) {
        this.tokenType = tokenType;
    }

    public static SyntaxScheme getDefaulSyntaxScheme() {
        SyntaxScheme scheme = new SyntaxScheme(false);
        Style[] defaultStyles = scheme.getStyles();
        Style[] styles = new Style[NUM_TOKEN_TYPES];
        System.arraycopy(defaultStyles, 0, styles, 0, defaultStyles.length);
        for (RSToken token: RSToken.values()) {
            styles[token.getTokenType()] = token.getStyle();
        }
        scheme.setStyles(styles);
        return scheme;
    }

    
}
