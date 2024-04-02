// Generated from corpusql\cql.g4 by ANTLR 4.9.3
package corpusql;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class cqlLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.9.3", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17, 
		T__17=18, T__18=19, T__19=20, T__20=21, T__21=22, T__22=23, T__23=24, 
		T__24=25, WHITESPACE=26, SPECIAL_TOKEN=27, SINGLE_LINE_COMMENT=28, MULTI_LINE_COMMENT=29, 
		WITHIN=30, CONTAINING=31, DEFAULT_VALUE=32, ROOT_DEP_OP=33, DEP_OP=34, 
		ALIGNMENT_OP=35, NAME=36, FLAGS=37, NUMBER=38, SETTINGS_OP=39, SETTINGS=40, 
		QUOTED_STRING=41, SINGLE_QUOTED_STRING=42;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
			"T__9", "T__10", "T__11", "T__12", "T__13", "T__14", "T__15", "T__16", 
			"T__17", "T__18", "T__19", "T__20", "T__21", "T__22", "T__23", "T__24", 
			"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", 
			"O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "DEP_SEPARATOR", 
			"ALIGNMENT_SEPARATOR", "DEP", "DEP_TARGET", "WHITESPACE", "SPECIAL_TOKEN", 
			"SINGLE_LINE_COMMENT", "MULTI_LINE_COMMENT", "WITHIN", "CONTAINING", 
			"DEFAULT_VALUE", "ROOT_DEP_OP", "DEP_OP", "ALIGNMENT_OP", "NAME", "FLAGS", 
			"NUMBER", "SETTINGS_OP", "SETTINGS", "QUOTED_STRING", "SINGLE_QUOTED_STRING"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'::'", "'='", "'!='", "'>='", "'<='", "'>'", "'<'", "'('", "')'", 
			"'!'", "'.'", "';'", "':'", "'/>'", "'</'", "'*'", "'+'", "'?'", "'{'", 
			"'}'", "','", "'&'", "'|'", "'->'", "'/'", null, null, null, null, null, 
			null, "'_'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, "WHITESPACE", "SPECIAL_TOKEN", "SINGLE_LINE_COMMENT", "MULTI_LINE_COMMENT", 
			"WITHIN", "CONTAINING", "DEFAULT_VALUE", "ROOT_DEP_OP", "DEP_OP", "ALIGNMENT_OP", 
			"NAME", "FLAGS", "NUMBER", "SETTINGS_OP", "SETTINGS", "QUOTED_STRING", 
			"SINGLE_QUOTED_STRING"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}


	public cqlLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "cql.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2,\u01b9\b\1\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t+\4"+
		",\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\4\63\t\63\4\64\t"+
		"\64\4\65\t\65\4\66\t\66\4\67\t\67\48\t8\49\t9\4:\t:\4;\t;\4<\t<\4=\t="+
		"\4>\t>\4?\t?\4@\t@\4A\tA\4B\tB\4C\tC\4D\tD\4E\tE\4F\tF\4G\tG\4H\tH\4I"+
		"\tI\3\2\3\2\3\2\3\3\3\3\3\4\3\4\3\4\3\5\3\5\3\5\3\6\3\6\3\6\3\7\3\7\3"+
		"\b\3\b\3\t\3\t\3\n\3\n\3\13\3\13\3\f\3\f\3\r\3\r\3\16\3\16\3\17\3\17\3"+
		"\17\3\20\3\20\3\20\3\21\3\21\3\22\3\22\3\23\3\23\3\24\3\24\3\25\3\25\3"+
		"\26\3\26\3\27\3\27\3\30\3\30\3\31\3\31\3\31\3\32\3\32\3\33\3\33\3\34\3"+
		"\34\3\35\3\35\3\36\3\36\3\37\3\37\3 \3 \3!\3!\3\"\3\"\3#\3#\3$\3$\3%\3"+
		"%\3&\3&\3\'\3\'\3(\3(\3)\3)\3*\3*\3+\3+\3,\3,\3-\3-\3.\3.\3/\3/\3\60\3"+
		"\60\3\61\3\61\3\62\3\62\3\63\3\63\3\64\3\64\3\65\3\65\3\66\3\66\3\67\3"+
		"\67\38\68\u0108\n8\r8\168\u0109\39\69\u010d\n9\r9\169\u010e\39\39\3:\3"+
		":\5:\u0115\n:\3;\3;\7;\u0119\n;\f;\16;\u011c\13;\3;\3;\3<\3<\3<\3<\7<"+
		"\u0124\n<\f<\16<\u0127\13<\3<\3<\3<\3<\3<\3=\3=\3=\3=\3=\3=\3=\3>\3>\3"+
		">\3>\3>\3>\3>\3>\3>\3>\3>\3?\3?\3@\3@\3@\3@\3@\3@\3@\7@\u0149\n@\f@\16"+
		"@\u014c\13@\5@\u014e\n@\3@\3@\3@\3@\3@\3A\5A\u0156\nA\3A\3A\3A\3A\3A\7"+
		"A\u015d\nA\fA\16A\u0160\13A\5A\u0162\nA\3A\3A\3A\3A\3A\3B\3B\3B\3B\3B"+
		"\7B\u016e\nB\fB\16B\u0171\13B\5B\u0173\nB\3B\3B\3B\3B\3B\3C\3C\7C\u017c"+
		"\nC\fC\16C\u017f\13C\3D\3D\3D\3D\6D\u0185\nD\rD\16D\u0186\3E\6E\u018a"+
		"\nE\rE\16E\u018b\3F\3F\3F\3F\3F\3F\3F\3F\3F\7F\u0197\nF\fF\16F\u019a\13"+
		"F\3G\3G\3G\7G\u019f\nG\fG\16G\u01a2\13G\3H\3H\3H\3H\7H\u01a8\nH\fH\16"+
		"H\u01ab\13H\3H\3H\3I\3I\3I\3I\7I\u01b3\nI\fI\16I\u01b6\13I\3I\3I\3\u0125"+
		"\2J\3\3\5\4\7\5\t\6\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33\17\35"+
		"\20\37\21!\22#\23%\24\'\25)\26+\27-\30/\31\61\32\63\33\65\2\67\29\2;\2"+
		"=\2?\2A\2C\2E\2G\2I\2K\2M\2O\2Q\2S\2U\2W\2Y\2[\2]\2_\2a\2c\2e\2g\2i\2"+
		"k\2m\2o\2q\34s\35u\36w\37y {!}\"\177#\u0081$\u0083%\u0085&\u0087\'\u0089"+
		"(\u008b)\u008d*\u008f+\u0091,\3\2$\4\2CCcc\4\2DDdd\4\2EEee\4\2FFff\4\2"+
		"GGgg\4\2HHhh\4\2IIii\4\2JJjj\4\2KKkk\4\2LLll\4\2MMmm\4\2NNnn\4\2OOoo\4"+
		"\2PPpp\4\2QQqq\4\2RRrr\4\2SSss\4\2TTtt\4\2UUuu\4\2VVvv\4\2WWww\4\2XXx"+
		"x\4\2YYyy\4\2ZZzz\4\2[[{{\4\2\\\\||\3\2//\6\2\62;C\\aac|\5\2\13\f\17\17"+
		"\"\"\4\2\f\f\17\17\5\2C\\aac|\7\2//\62;C\\aac|\3\2\62;\4\2$$^^\3\2\2\u01b1"+
		"\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2"+
		"\2\2\2\17\3\2\2\2\2\21\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2"+
		"\2\31\3\2\2\2\2\33\3\2\2\2\2\35\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2#\3\2"+
		"\2\2\2%\3\2\2\2\2\'\3\2\2\2\2)\3\2\2\2\2+\3\2\2\2\2-\3\2\2\2\2/\3\2\2"+
		"\2\2\61\3\2\2\2\2\63\3\2\2\2\2q\3\2\2\2\2s\3\2\2\2\2u\3\2\2\2\2w\3\2\2"+
		"\2\2y\3\2\2\2\2{\3\2\2\2\2}\3\2\2\2\2\177\3\2\2\2\2\u0081\3\2\2\2\2\u0083"+
		"\3\2\2\2\2\u0085\3\2\2\2\2\u0087\3\2\2\2\2\u0089\3\2\2\2\2\u008b\3\2\2"+
		"\2\2\u008d\3\2\2\2\2\u008f\3\2\2\2\2\u0091\3\2\2\2\3\u0093\3\2\2\2\5\u0096"+
		"\3\2\2\2\7\u0098\3\2\2\2\t\u009b\3\2\2\2\13\u009e\3\2\2\2\r\u00a1\3\2"+
		"\2\2\17\u00a3\3\2\2\2\21\u00a5\3\2\2\2\23\u00a7\3\2\2\2\25\u00a9\3\2\2"+
		"\2\27\u00ab\3\2\2\2\31\u00ad\3\2\2\2\33\u00af\3\2\2\2\35\u00b1\3\2\2\2"+
		"\37\u00b4\3\2\2\2!\u00b7\3\2\2\2#\u00b9\3\2\2\2%\u00bb\3\2\2\2\'\u00bd"+
		"\3\2\2\2)\u00bf\3\2\2\2+\u00c1\3\2\2\2-\u00c3\3\2\2\2/\u00c5\3\2\2\2\61"+
		"\u00c7\3\2\2\2\63\u00ca\3\2\2\2\65\u00cc\3\2\2\2\67\u00ce\3\2\2\29\u00d0"+
		"\3\2\2\2;\u00d2\3\2\2\2=\u00d4\3\2\2\2?\u00d6\3\2\2\2A\u00d8\3\2\2\2C"+
		"\u00da\3\2\2\2E\u00dc\3\2\2\2G\u00de\3\2\2\2I\u00e0\3\2\2\2K\u00e2\3\2"+
		"\2\2M\u00e4\3\2\2\2O\u00e6\3\2\2\2Q\u00e8\3\2\2\2S\u00ea\3\2\2\2U\u00ec"+
		"\3\2\2\2W\u00ee\3\2\2\2Y\u00f0\3\2\2\2[\u00f2\3\2\2\2]\u00f4\3\2\2\2_"+
		"\u00f6\3\2\2\2a\u00f8\3\2\2\2c\u00fa\3\2\2\2e\u00fc\3\2\2\2g\u00fe\3\2"+
		"\2\2i\u0100\3\2\2\2k\u0102\3\2\2\2m\u0104\3\2\2\2o\u0107\3\2\2\2q\u010c"+
		"\3\2\2\2s\u0114\3\2\2\2u\u0116\3\2\2\2w\u011f\3\2\2\2y\u012d\3\2\2\2{"+
		"\u0134\3\2\2\2}\u013f\3\2\2\2\177\u0141\3\2\2\2\u0081\u0155\3\2\2\2\u0083"+
		"\u0168\3\2\2\2\u0085\u0179\3\2\2\2\u0087\u0180\3\2\2\2\u0089\u0189\3\2"+
		"\2\2\u008b\u018d\3\2\2\2\u008d\u019b\3\2\2\2\u008f\u01a3\3\2\2\2\u0091"+
		"\u01ae\3\2\2\2\u0093\u0094\7<\2\2\u0094\u0095\7<\2\2\u0095\4\3\2\2\2\u0096"+
		"\u0097\7?\2\2\u0097\6\3\2\2\2\u0098\u0099\7#\2\2\u0099\u009a\7?\2\2\u009a"+
		"\b\3\2\2\2\u009b\u009c\7@\2\2\u009c\u009d\7?\2\2\u009d\n\3\2\2\2\u009e"+
		"\u009f\7>\2\2\u009f\u00a0\7?\2\2\u00a0\f\3\2\2\2\u00a1\u00a2\7@\2\2\u00a2"+
		"\16\3\2\2\2\u00a3\u00a4\7>\2\2\u00a4\20\3\2\2\2\u00a5\u00a6\7*\2\2\u00a6"+
		"\22\3\2\2\2\u00a7\u00a8\7+\2\2\u00a8\24\3\2\2\2\u00a9\u00aa\7#\2\2\u00aa"+
		"\26\3\2\2\2\u00ab\u00ac\7\60\2\2\u00ac\30\3\2\2\2\u00ad\u00ae\7=\2\2\u00ae"+
		"\32\3\2\2\2\u00af\u00b0\7<\2\2\u00b0\34\3\2\2\2\u00b1\u00b2\7\61\2\2\u00b2"+
		"\u00b3\7@\2\2\u00b3\36\3\2\2\2\u00b4\u00b5\7>\2\2\u00b5\u00b6\7\61\2\2"+
		"\u00b6 \3\2\2\2\u00b7\u00b8\7,\2\2\u00b8\"\3\2\2\2\u00b9\u00ba\7-\2\2"+
		"\u00ba$\3\2\2\2\u00bb\u00bc\7A\2\2\u00bc&\3\2\2\2\u00bd\u00be\7}\2\2\u00be"+
		"(\3\2\2\2\u00bf\u00c0\7\177\2\2\u00c0*\3\2\2\2\u00c1\u00c2\7.\2\2\u00c2"+
		",\3\2\2\2\u00c3\u00c4\7(\2\2\u00c4.\3\2\2\2\u00c5\u00c6\7~\2\2\u00c6\60"+
		"\3\2\2\2\u00c7\u00c8\7/\2\2\u00c8\u00c9\7@\2\2\u00c9\62\3\2\2\2\u00ca"+
		"\u00cb\7\61\2\2\u00cb\64\3\2\2\2\u00cc\u00cd\t\2\2\2\u00cd\66\3\2\2\2"+
		"\u00ce\u00cf\t\3\2\2\u00cf8\3\2\2\2\u00d0\u00d1\t\4\2\2\u00d1:\3\2\2\2"+
		"\u00d2\u00d3\t\5\2\2\u00d3<\3\2\2\2\u00d4\u00d5\t\6\2\2\u00d5>\3\2\2\2"+
		"\u00d6\u00d7\t\7\2\2\u00d7@\3\2\2\2\u00d8\u00d9\t\b\2\2\u00d9B\3\2\2\2"+
		"\u00da\u00db\t\t\2\2\u00dbD\3\2\2\2\u00dc\u00dd\t\n\2\2\u00ddF\3\2\2\2"+
		"\u00de\u00df\t\13\2\2\u00dfH\3\2\2\2\u00e0\u00e1\t\f\2\2\u00e1J\3\2\2"+
		"\2\u00e2\u00e3\t\r\2\2\u00e3L\3\2\2\2\u00e4\u00e5\t\16\2\2\u00e5N\3\2"+
		"\2\2\u00e6\u00e7\t\17\2\2\u00e7P\3\2\2\2\u00e8\u00e9\t\20\2\2\u00e9R\3"+
		"\2\2\2\u00ea\u00eb\t\21\2\2\u00ebT\3\2\2\2\u00ec\u00ed\t\22\2\2\u00ed"+
		"V\3\2\2\2\u00ee\u00ef\t\23\2\2\u00efX\3\2\2\2\u00f0\u00f1\t\24\2\2\u00f1"+
		"Z\3\2\2\2\u00f2\u00f3\t\25\2\2\u00f3\\\3\2\2\2\u00f4\u00f5\t\26\2\2\u00f5"+
		"^\3\2\2\2\u00f6\u00f7\t\27\2\2\u00f7`\3\2\2\2\u00f8\u00f9\t\30\2\2\u00f9"+
		"b\3\2\2\2\u00fa\u00fb\t\31\2\2\u00fbd\3\2\2\2\u00fc\u00fd\t\32\2\2\u00fd"+
		"f\3\2\2\2\u00fe\u00ff\t\33\2\2\u00ffh\3\2\2\2\u0100\u0101\7/\2\2\u0101"+
		"j\3\2\2\2\u0102\u0103\7?\2\2\u0103l\3\2\2\2\u0104\u0105\n\34\2\2\u0105"+
		"n\3\2\2\2\u0106\u0108\t\35\2\2\u0107\u0106\3\2\2\2\u0108\u0109\3\2\2\2"+
		"\u0109\u0107\3\2\2\2\u0109\u010a\3\2\2\2\u010ap\3\2\2\2\u010b\u010d\t"+
		"\36\2\2\u010c\u010b\3\2\2\2\u010d\u010e\3\2\2\2\u010e\u010c\3\2\2\2\u010e"+
		"\u010f\3\2\2\2\u010f\u0110\3\2\2\2\u0110\u0111\b9\2\2\u0111r\3\2\2\2\u0112"+
		"\u0115\5u;\2\u0113\u0115\5w<\2\u0114\u0112\3\2\2\2\u0114\u0113\3\2\2\2"+
		"\u0115t\3\2\2\2\u0116\u011a\7%\2\2\u0117\u0119\n\37\2\2\u0118\u0117\3"+
		"\2\2\2\u0119\u011c\3\2\2\2\u011a\u0118\3\2\2\2\u011a\u011b\3\2\2\2\u011b"+
		"\u011d\3\2\2\2\u011c\u011a\3\2\2\2\u011d\u011e\b;\2\2\u011ev\3\2\2\2\u011f"+
		"\u0120\7\61\2\2\u0120\u0121\7,\2\2\u0121\u0125\3\2\2\2\u0122\u0124\13"+
		"\2\2\2\u0123\u0122\3\2\2\2\u0124\u0127\3\2\2\2\u0125\u0126\3\2\2\2\u0125"+
		"\u0123\3\2\2\2\u0126\u0128\3\2\2\2\u0127\u0125\3\2\2\2\u0128\u0129\7,"+
		"\2\2\u0129\u012a\7\61\2\2\u012a\u012b\3\2\2\2\u012b\u012c\b<\2\2\u012c"+
		"x\3\2\2\2\u012d\u012e\5a\61\2\u012e\u012f\5E#\2\u012f\u0130\5[.\2\u0130"+
		"\u0131\5C\"\2\u0131\u0132\5E#\2\u0132\u0133\5O(\2\u0133z\3\2\2\2\u0134"+
		"\u0135\59\35\2\u0135\u0136\5Q)\2\u0136\u0137\5O(\2\u0137\u0138\5[.\2\u0138"+
		"\u0139\5\65\33\2\u0139\u013a\5E#\2\u013a\u013b\5O(\2\u013b\u013c\5E#\2"+
		"\u013c\u013d\5O(\2\u013d\u013e\5A!\2\u013e|\3\2\2\2\u013f\u0140\7a\2\2"+
		"\u0140~\3\2\2\2\u0141\u0142\7`\2\2\u0142\u0143\7/\2\2\u0143\u014d\3\2"+
		"\2\2\u0144\u014a\5m\67\2\u0145\u0146\5i\65\2\u0146\u0147\5m\67\2\u0147"+
		"\u0149\3\2\2\2\u0148\u0145\3\2\2\2\u0149\u014c\3\2\2\2\u014a\u0148\3\2"+
		"\2\2\u014a\u014b\3\2\2\2\u014b\u014e\3\2\2\2\u014c\u014a\3\2\2\2\u014d"+
		"\u0144\3\2\2\2\u014d\u014e\3\2\2\2\u014e\u014f\3\2\2\2\u014f\u0150\7/"+
		"\2\2\u0150\u0151\7@\2\2\u0151\u0152\3\2\2\2\u0152\u0153\5o8\2\u0153\u0080"+
		"\3\2\2\2\u0154\u0156\7#\2\2\u0155\u0154\3\2\2\2\u0155\u0156\3\2\2\2\u0156"+
		"\u0157\3\2\2\2\u0157\u0161\7/\2\2\u0158\u015e\5m\67\2\u0159\u015a\5i\65"+
		"\2\u015a\u015b\5m\67\2\u015b\u015d\3\2\2\2\u015c\u0159\3\2\2\2\u015d\u0160"+
		"\3\2\2\2\u015e\u015c\3\2\2\2\u015e\u015f\3\2\2\2\u015f\u0162\3\2\2\2\u0160"+
		"\u015e\3\2\2\2\u0161\u0158\3\2\2\2\u0161\u0162\3\2\2\2\u0162\u0163\3\2"+
		"\2\2\u0163\u0164\7/\2\2\u0164\u0165\7@\2\2\u0165\u0166\3\2\2\2\u0166\u0167"+
		"\5o8\2\u0167\u0082\3\2\2\2\u0168\u0172\7?\2\2\u0169\u016f\5m\67\2\u016a"+
		"\u016b\5k\66\2\u016b\u016c\5m\67\2\u016c\u016e\3\2\2\2\u016d\u016a\3\2"+
		"\2\2\u016e\u0171\3\2\2\2\u016f\u016d\3\2\2\2\u016f\u0170\3\2\2\2\u0170"+
		"\u0173\3\2\2\2\u0171\u016f\3\2\2\2\u0172\u0169\3\2\2\2\u0172\u0173\3\2"+
		"\2\2\u0173\u0174\3\2\2\2\u0174\u0175\7?\2\2\u0175\u0176\7@\2\2\u0176\u0177"+
		"\3\2\2\2\u0177\u0178\5o8\2\u0178\u0084\3\2\2\2\u0179\u017d\t \2\2\u017a"+
		"\u017c\t!\2\2\u017b\u017a\3\2\2\2\u017c\u017f\3\2\2\2\u017d\u017b\3\2"+
		"\2\2\u017d\u017e\3\2\2\2\u017e\u0086\3\2\2\2\u017f\u017d\3\2\2\2\u0180"+
		"\u0184\7\'\2\2\u0181\u0185\59\35\2\u0182\u0185\5;\36\2\u0183\u0185\5K"+
		"&\2\u0184\u0181\3\2\2\2\u0184\u0182\3\2\2\2\u0184\u0183\3\2\2\2\u0185"+
		"\u0186\3\2\2\2\u0186\u0184\3\2\2\2\u0186\u0187\3\2\2\2\u0187\u0088\3\2"+
		"\2\2\u0188\u018a\t\"\2\2\u0189\u0188\3\2\2\2\u018a\u018b\3\2\2\2\u018b"+
		"\u0189\3\2\2\2\u018b\u018c\3\2\2\2\u018c\u008a\3\2\2\2\u018d\u018e\7B"+
		"\2\2\u018e\u018f\5\u0085C\2\u018f\u0190\7?\2\2\u0190\u0198\5\u0085C\2"+
		"\u0191\u0192\7.\2\2\u0192\u0193\5\u0085C\2\u0193\u0194\7?\2\2\u0194\u0195"+
		"\5\u0085C\2\u0195\u0197\3\2\2\2\u0196\u0191\3\2\2\2\u0197\u019a\3\2\2"+
		"\2\u0198\u0196\3\2\2\2\u0198\u0199\3\2\2\2\u0199\u008c\3\2\2\2\u019a\u0198"+
		"\3\2\2\2\u019b\u01a0\5\u008bF\2\u019c\u019d\7.\2\2\u019d\u019f\5\u008b"+
		"F\2\u019e\u019c\3\2\2\2\u019f\u01a2\3\2\2\2\u01a0\u019e\3\2\2\2\u01a0"+
		"\u01a1\3\2\2\2\u01a1\u008e\3\2\2\2\u01a2\u01a0\3\2\2\2\u01a3\u01a9\7$"+
		"\2\2\u01a4\u01a8\n#\2\2\u01a5\u01a6\7^\2\2\u01a6\u01a8\n$\2\2\u01a7\u01a4"+
		"\3\2\2\2\u01a7\u01a5\3\2\2\2\u01a8\u01ab\3\2\2\2\u01a9\u01a7\3\2\2\2\u01a9"+
		"\u01aa\3\2\2\2\u01aa\u01ac\3\2\2\2\u01ab\u01a9\3\2\2\2\u01ac\u01ad\7$"+
		"\2\2\u01ad\u0090\3\2\2\2\u01ae\u01b4\7)\2\2\u01af\u01b3\n#\2\2\u01b0\u01b1"+
		"\7^\2\2\u01b1\u01b3\n$\2\2\u01b2\u01af\3\2\2\2\u01b2\u01b0\3\2\2\2\u01b3"+
		"\u01b6\3\2\2\2\u01b4\u01b2\3\2\2\2\u01b4\u01b5\3\2\2\2\u01b5\u01b7\3\2"+
		"\2\2\u01b6\u01b4\3\2\2\2\u01b7\u01b8\7)\2\2\u01b8\u0092\3\2\2\2\31\2\u0109"+
		"\u010e\u0114\u011a\u0125\u014a\u014d\u0155\u015e\u0161\u016f\u0172\u017d"+
		"\u0184\u0186\u018b\u0198\u01a0\u01a7\u01a9\u01b2\u01b4\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}