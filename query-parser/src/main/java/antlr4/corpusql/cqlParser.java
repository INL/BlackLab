// Generated from corpusql\cql.g4 by ANTLR 4.9.3
package corpusql;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class cqlParser extends Parser {
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
	public static final int
		RULE_textpattern = 0, RULE_settingsQuery = 1, RULE_constrainedQuery = 2, 
		RULE_constraint = 3, RULE_simpleConstraint = 4, RULE_comparisonOperator = 5, 
		RULE_constraintValue = 6, RULE_relationQuery = 7, RULE_childRelation = 8, 
		RULE_relationType = 9, RULE_rootRelationType = 10, RULE_tag = 11, RULE_quotedString = 12, 
		RULE_attributes = 13, RULE_repetitionAmount = 14, RULE_complexQuery = 15, 
		RULE_queryOperator = 16, RULE_simpleQuery = 17, RULE_booleanOperator = 18, 
		RULE_sequence = 19, RULE_captureQuery = 20, RULE_sequencePartNoCapture = 21, 
		RULE_queryFuctionCall = 22, RULE_commaSeparatedParamList = 23, RULE_functionParam = 24, 
		RULE_captureLabel = 25, RULE_position = 26, RULE_flags = 27, RULE_positionWord = 28, 
		RULE_positionLong = 29, RULE_positionLongPart = 30, RULE_attValuePair = 31, 
		RULE_annotName = 32, RULE_valuePart = 33;
	private static String[] makeRuleNames() {
		return new String[] {
			"textpattern", "settingsQuery", "constrainedQuery", "constraint", "simpleConstraint", 
			"comparisonOperator", "constraintValue", "relationQuery", "childRelation", 
			"relationType", "rootRelationType", "tag", "quotedString", "attributes", 
			"repetitionAmount", "complexQuery", "queryOperator", "simpleQuery", "booleanOperator", 
			"sequence", "captureQuery", "sequencePartNoCapture", "queryFuctionCall", 
			"commaSeparatedParamList", "functionParam", "captureLabel", "position", 
			"flags", "positionWord", "positionLong", "positionLongPart", "attValuePair", 
			"annotName", "valuePart"
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

	@Override
	public String getGrammarFileName() { return "cql.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public cqlParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	public static class TextpatternContext extends ParserRuleContext {
		public SettingsQueryContext settingsQuery() {
			return getRuleContext(SettingsQueryContext.class,0);
		}
		public TerminalNode EOF() { return getToken(cqlParser.EOF, 0); }
		public TextpatternContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_textpattern; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterTextpattern(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitTextpattern(this);
		}
	}

	public final TextpatternContext textpattern() throws RecognitionException {
		TextpatternContext _localctx = new TextpatternContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_textpattern);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(68);
			settingsQuery();
			setState(69);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class SettingsQueryContext extends ParserRuleContext {
		public TerminalNode SETTINGS_OP() { return getToken(cqlParser.SETTINGS_OP, 0); }
		public ConstrainedQueryContext constrainedQuery() {
			return getRuleContext(ConstrainedQueryContext.class,0);
		}
		public SettingsQueryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_settingsQuery; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterSettingsQuery(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitSettingsQuery(this);
		}
	}

	public final SettingsQueryContext settingsQuery() throws RecognitionException {
		SettingsQueryContext _localctx = new SettingsQueryContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_settingsQuery);
		try {
			setState(73);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case SETTINGS_OP:
				enterOuterAlt(_localctx, 1);
				{
				setState(71);
				match(SETTINGS_OP);
				}
				break;
			case T__6:
			case T__7:
			case T__9:
			case DEFAULT_VALUE:
			case ROOT_DEP_OP:
			case NAME:
			case NUMBER:
			case QUOTED_STRING:
			case SINGLE_QUOTED_STRING:
				enterOuterAlt(_localctx, 2);
				{
				setState(72);
				constrainedQuery();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ConstrainedQueryContext extends ParserRuleContext {
		public RelationQueryContext relationQuery() {
			return getRuleContext(RelationQueryContext.class,0);
		}
		public ConstraintContext constraint() {
			return getRuleContext(ConstraintContext.class,0);
		}
		public ConstrainedQueryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constrainedQuery; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterConstrainedQuery(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitConstrainedQuery(this);
		}
	}

	public final ConstrainedQueryContext constrainedQuery() throws RecognitionException {
		ConstrainedQueryContext _localctx = new ConstrainedQueryContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_constrainedQuery);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(75);
			relationQuery();
			setState(78);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__0) {
				{
				setState(76);
				match(T__0);
				setState(77);
				constraint();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ConstraintContext extends ParserRuleContext {
		public SimpleConstraintContext simpleConstraint() {
			return getRuleContext(SimpleConstraintContext.class,0);
		}
		public BooleanOperatorContext booleanOperator() {
			return getRuleContext(BooleanOperatorContext.class,0);
		}
		public ConstraintContext constraint() {
			return getRuleContext(ConstraintContext.class,0);
		}
		public ConstraintContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constraint; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterConstraint(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitConstraint(this);
		}
	}

	public final ConstraintContext constraint() throws RecognitionException {
		ConstraintContext _localctx = new ConstraintContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_constraint);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(80);
			simpleConstraint();
			setState(84);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__21) | (1L << T__22) | (1L << T__23))) != 0)) {
				{
				setState(81);
				booleanOperator();
				setState(82);
				constraint();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class SimpleConstraintContext extends ParserRuleContext {
		public List<ConstraintValueContext> constraintValue() {
			return getRuleContexts(ConstraintValueContext.class);
		}
		public ConstraintValueContext constraintValue(int i) {
			return getRuleContext(ConstraintValueContext.class,i);
		}
		public ComparisonOperatorContext comparisonOperator() {
			return getRuleContext(ComparisonOperatorContext.class,0);
		}
		public SimpleConstraintContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simpleConstraint; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterSimpleConstraint(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitSimpleConstraint(this);
		}
	}

	public final SimpleConstraintContext simpleConstraint() throws RecognitionException {
		SimpleConstraintContext _localctx = new SimpleConstraintContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_simpleConstraint);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(86);
			constraintValue();
			setState(90);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__1) | (1L << T__2) | (1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6))) != 0)) {
				{
				setState(87);
				comparisonOperator();
				setState(88);
				constraintValue();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ComparisonOperatorContext extends ParserRuleContext {
		public ComparisonOperatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comparisonOperator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterComparisonOperator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitComparisonOperator(this);
		}
	}

	public final ComparisonOperatorContext comparisonOperator() throws RecognitionException {
		ComparisonOperatorContext _localctx = new ComparisonOperatorContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_comparisonOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(92);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__1) | (1L << T__2) | (1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ConstraintValueContext extends ParserRuleContext {
		public QuotedStringContext quotedString() {
			return getRuleContext(QuotedStringContext.class,0);
		}
		public ConstraintContext constraint() {
			return getRuleContext(ConstraintContext.class,0);
		}
		public ConstraintValueContext constraintValue() {
			return getRuleContext(ConstraintValueContext.class,0);
		}
		public TerminalNode NAME() { return getToken(cqlParser.NAME, 0); }
		public CaptureLabelContext captureLabel() {
			return getRuleContext(CaptureLabelContext.class,0);
		}
		public ConstraintValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constraintValue; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterConstraintValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitConstraintValue(this);
		}
	}

	public final ConstraintValueContext constraintValue() throws RecognitionException {
		ConstraintValueContext _localctx = new ConstraintValueContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_constraintValue);
		int _la;
		try {
			setState(111);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,5,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(94);
				quotedString();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(95);
				match(T__7);
				setState(96);
				constraint();
				setState(97);
				match(T__8);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(99);
				match(T__9);
				setState(100);
				constraintValue();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(101);
				match(NAME);
				setState(102);
				match(T__7);
				setState(103);
				captureLabel();
				setState(104);
				match(T__8);
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(106);
				captureLabel();
				setState(109);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__10) {
					{
					setState(107);
					match(T__10);
					setState(108);
					match(NAME);
					}
				}

				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class RelationQueryContext extends ParserRuleContext {
		public ComplexQueryContext complexQuery() {
			return getRuleContext(ComplexQueryContext.class,0);
		}
		public List<ChildRelationContext> childRelation() {
			return getRuleContexts(ChildRelationContext.class);
		}
		public ChildRelationContext childRelation(int i) {
			return getRuleContext(ChildRelationContext.class,i);
		}
		public RootRelationTypeContext rootRelationType() {
			return getRuleContext(RootRelationTypeContext.class,0);
		}
		public RelationQueryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_relationQuery; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterRelationQuery(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitRelationQuery(this);
		}
	}

	public final RelationQueryContext relationQuery() throws RecognitionException {
		RelationQueryContext _localctx = new RelationQueryContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_relationQuery);
		int _la;
		try {
			int _alt;
			setState(125);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,8,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(113);
				complexQuery();
				setState(122);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << DEP_OP) | (1L << ALIGNMENT_OP) | (1L << NAME) | (1L << NUMBER))) != 0)) {
					{
					setState(114);
					childRelation();
					setState(119);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,6,_ctx);
					while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
						if ( _alt==1 ) {
							{
							{
							setState(115);
							match(T__11);
							setState(116);
							childRelation();
							}
							} 
						}
						setState(121);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,6,_ctx);
					}
					}
				}

				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(124);
				rootRelationType();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ChildRelationContext extends ParserRuleContext {
		public RelationTypeContext relationType() {
			return getRuleContext(RelationTypeContext.class,0);
		}
		public RelationQueryContext relationQuery() {
			return getRuleContext(RelationQueryContext.class,0);
		}
		public CaptureLabelContext captureLabel() {
			return getRuleContext(CaptureLabelContext.class,0);
		}
		public ChildRelationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_childRelation; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterChildRelation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitChildRelation(this);
		}
	}

	public final ChildRelationContext childRelation() throws RecognitionException {
		ChildRelationContext _localctx = new ChildRelationContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_childRelation);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(130);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NAME || _la==NUMBER) {
				{
				setState(127);
				captureLabel();
				setState(128);
				match(T__12);
				}
			}

			setState(132);
			relationType();
			setState(133);
			relationQuery();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class RelationTypeContext extends ParserRuleContext {
		public TerminalNode DEP_OP() { return getToken(cqlParser.DEP_OP, 0); }
		public TerminalNode ALIGNMENT_OP() { return getToken(cqlParser.ALIGNMENT_OP, 0); }
		public RelationTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_relationType; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterRelationType(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitRelationType(this);
		}
	}

	public final RelationTypeContext relationType() throws RecognitionException {
		RelationTypeContext _localctx = new RelationTypeContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_relationType);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(135);
			_la = _input.LA(1);
			if ( !(_la==DEP_OP || _la==ALIGNMENT_OP) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class RootRelationTypeContext extends ParserRuleContext {
		public TerminalNode ROOT_DEP_OP() { return getToken(cqlParser.ROOT_DEP_OP, 0); }
		public RelationQueryContext relationQuery() {
			return getRuleContext(RelationQueryContext.class,0);
		}
		public CaptureLabelContext captureLabel() {
			return getRuleContext(CaptureLabelContext.class,0);
		}
		public RootRelationTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_rootRelationType; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterRootRelationType(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitRootRelationType(this);
		}
	}

	public final RootRelationTypeContext rootRelationType() throws RecognitionException {
		RootRelationTypeContext _localctx = new RootRelationTypeContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_rootRelationType);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(140);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NAME || _la==NUMBER) {
				{
				setState(137);
				captureLabel();
				setState(138);
				match(T__12);
				}
			}

			setState(142);
			match(ROOT_DEP_OP);
			setState(143);
			relationQuery();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class TagContext extends ParserRuleContext {
		public List<TerminalNode> NAME() { return getTokens(cqlParser.NAME); }
		public TerminalNode NAME(int i) {
			return getToken(cqlParser.NAME, i);
		}
		public List<AttributesContext> attributes() {
			return getRuleContexts(AttributesContext.class);
		}
		public AttributesContext attributes(int i) {
			return getRuleContext(AttributesContext.class,i);
		}
		public TextpatternContext textpattern() {
			return getRuleContext(TextpatternContext.class,0);
		}
		public TagContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_tag; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterTag(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitTag(this);
		}
	}

	public final TagContext tag() throws RecognitionException {
		TagContext _localctx = new TagContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_tag);
		int _la;
		try {
			setState(168);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,13,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(145);
				match(T__6);
				setState(146);
				match(NAME);
				setState(150);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==NAME) {
					{
					{
					setState(147);
					attributes();
					}
					}
					setState(152);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(153);
				match(T__13);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(154);
				match(T__6);
				setState(155);
				match(NAME);
				setState(159);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==NAME) {
					{
					{
					setState(156);
					attributes();
					}
					}
					setState(161);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(162);
				match(T__5);
				setState(163);
				textpattern();
				setState(164);
				match(T__14);
				setState(165);
				match(NAME);
				setState(166);
				match(T__5);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class QuotedStringContext extends ParserRuleContext {
		public TerminalNode QUOTED_STRING() { return getToken(cqlParser.QUOTED_STRING, 0); }
		public TerminalNode SINGLE_QUOTED_STRING() { return getToken(cqlParser.SINGLE_QUOTED_STRING, 0); }
		public QuotedStringContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_quotedString; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterQuotedString(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitQuotedString(this);
		}
	}

	public final QuotedStringContext quotedString() throws RecognitionException {
		QuotedStringContext _localctx = new QuotedStringContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_quotedString);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(170);
			_la = _input.LA(1);
			if ( !(_la==QUOTED_STRING || _la==SINGLE_QUOTED_STRING) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AttributesContext extends ParserRuleContext {
		public TerminalNode NAME() { return getToken(cqlParser.NAME, 0); }
		public QuotedStringContext quotedString() {
			return getRuleContext(QuotedStringContext.class,0);
		}
		public AttributesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_attributes; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterAttributes(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitAttributes(this);
		}
	}

	public final AttributesContext attributes() throws RecognitionException {
		AttributesContext _localctx = new AttributesContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_attributes);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(172);
			match(NAME);
			setState(173);
			match(T__1);
			setState(174);
			quotedString();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class RepetitionAmountContext extends ParserRuleContext {
		public List<TerminalNode> NUMBER() { return getTokens(cqlParser.NUMBER); }
		public TerminalNode NUMBER(int i) {
			return getToken(cqlParser.NUMBER, i);
		}
		public RepetitionAmountContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_repetitionAmount; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterRepetitionAmount(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitRepetitionAmount(this);
		}
	}

	public final RepetitionAmountContext repetitionAmount() throws RecognitionException {
		RepetitionAmountContext _localctx = new RepetitionAmountContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_repetitionAmount);
		int _la;
		try {
			setState(189);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,15,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(176);
				match(T__15);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(177);
				match(T__16);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(178);
				match(T__17);
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(179);
				match(T__18);
				setState(180);
				match(NUMBER);
				setState(181);
				match(T__19);
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(182);
				match(T__18);
				setState(183);
				match(NUMBER);
				setState(184);
				match(T__20);
				setState(186);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NUMBER) {
					{
					setState(185);
					match(NUMBER);
					}
				}

				setState(188);
				match(T__19);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ComplexQueryContext extends ParserRuleContext {
		public SimpleQueryContext simpleQuery() {
			return getRuleContext(SimpleQueryContext.class,0);
		}
		public QueryOperatorContext queryOperator() {
			return getRuleContext(QueryOperatorContext.class,0);
		}
		public ComplexQueryContext complexQuery() {
			return getRuleContext(ComplexQueryContext.class,0);
		}
		public ComplexQueryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_complexQuery; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterComplexQuery(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitComplexQuery(this);
		}
	}

	public final ComplexQueryContext complexQuery() throws RecognitionException {
		ComplexQueryContext _localctx = new ComplexQueryContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_complexQuery);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(191);
			simpleQuery();
			setState(195);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITHIN || _la==CONTAINING) {
				{
				setState(192);
				queryOperator();
				setState(193);
				complexQuery();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class QueryOperatorContext extends ParserRuleContext {
		public TerminalNode WITHIN() { return getToken(cqlParser.WITHIN, 0); }
		public TerminalNode CONTAINING() { return getToken(cqlParser.CONTAINING, 0); }
		public QueryOperatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_queryOperator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterQueryOperator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitQueryOperator(this);
		}
	}

	public final QueryOperatorContext queryOperator() throws RecognitionException {
		QueryOperatorContext _localctx = new QueryOperatorContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_queryOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(197);
			_la = _input.LA(1);
			if ( !(_la==WITHIN || _la==CONTAINING) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class SimpleQueryContext extends ParserRuleContext {
		public SequenceContext sequence() {
			return getRuleContext(SequenceContext.class,0);
		}
		public BooleanOperatorContext booleanOperator() {
			return getRuleContext(BooleanOperatorContext.class,0);
		}
		public SimpleQueryContext simpleQuery() {
			return getRuleContext(SimpleQueryContext.class,0);
		}
		public SimpleQueryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simpleQuery; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterSimpleQuery(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitSimpleQuery(this);
		}
	}

	public final SimpleQueryContext simpleQuery() throws RecognitionException {
		SimpleQueryContext _localctx = new SimpleQueryContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_simpleQuery);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(199);
			sequence();
			setState(203);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__21) | (1L << T__22) | (1L << T__23))) != 0)) {
				{
				setState(200);
				booleanOperator();
				setState(201);
				simpleQuery();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class BooleanOperatorContext extends ParserRuleContext {
		public BooleanOperatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_booleanOperator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterBooleanOperator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitBooleanOperator(this);
		}
	}

	public final BooleanOperatorContext booleanOperator() throws RecognitionException {
		BooleanOperatorContext _localctx = new BooleanOperatorContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_booleanOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(205);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__21) | (1L << T__22) | (1L << T__23))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class SequenceContext extends ParserRuleContext {
		public CaptureQueryContext captureQuery() {
			return getRuleContext(CaptureQueryContext.class,0);
		}
		public SequenceContext sequence() {
			return getRuleContext(SequenceContext.class,0);
		}
		public SequenceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sequence; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterSequence(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitSequence(this);
		}
	}

	public final SequenceContext sequence() throws RecognitionException {
		SequenceContext _localctx = new SequenceContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_sequence);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(207);
			captureQuery();
			setState(209);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,18,_ctx) ) {
			case 1:
				{
				setState(208);
				sequence();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class CaptureQueryContext extends ParserRuleContext {
		public SequencePartNoCaptureContext sequencePartNoCapture() {
			return getRuleContext(SequencePartNoCaptureContext.class,0);
		}
		public CaptureLabelContext captureLabel() {
			return getRuleContext(CaptureLabelContext.class,0);
		}
		public CaptureQueryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_captureQuery; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterCaptureQuery(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitCaptureQuery(this);
		}
	}

	public final CaptureQueryContext captureQuery() throws RecognitionException {
		CaptureQueryContext _localctx = new CaptureQueryContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_captureQuery);
		try {
			setState(216);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,19,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(211);
				sequencePartNoCapture();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(212);
				captureLabel();
				setState(213);
				match(T__12);
				setState(214);
				sequencePartNoCapture();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class SequencePartNoCaptureContext extends ParserRuleContext {
		public TagContext tag() {
			return getRuleContext(TagContext.class,0);
		}
		public PositionContext position() {
			return getRuleContext(PositionContext.class,0);
		}
		public ConstrainedQueryContext constrainedQuery() {
			return getRuleContext(ConstrainedQueryContext.class,0);
		}
		public QueryFuctionCallContext queryFuctionCall() {
			return getRuleContext(QueryFuctionCallContext.class,0);
		}
		public RepetitionAmountContext repetitionAmount() {
			return getRuleContext(RepetitionAmountContext.class,0);
		}
		public SequencePartNoCaptureContext sequencePartNoCapture() {
			return getRuleContext(SequencePartNoCaptureContext.class,0);
		}
		public SequencePartNoCaptureContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sequencePartNoCapture; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterSequencePartNoCapture(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitSequencePartNoCapture(this);
		}
	}

	public final SequencePartNoCaptureContext sequencePartNoCapture() throws RecognitionException {
		SequencePartNoCaptureContext _localctx = new SequencePartNoCaptureContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_sequencePartNoCapture);
		int _la;
		try {
			setState(232);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__6:
			case T__7:
			case DEFAULT_VALUE:
			case NAME:
			case QUOTED_STRING:
			case SINGLE_QUOTED_STRING:
				enterOuterAlt(_localctx, 1);
				{
				{
				setState(225);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,20,_ctx) ) {
				case 1:
					{
					setState(218);
					tag();
					}
					break;
				case 2:
					{
					setState(219);
					position();
					}
					break;
				case 3:
					{
					setState(220);
					match(T__7);
					setState(221);
					constrainedQuery();
					setState(222);
					match(T__8);
					}
					break;
				case 4:
					{
					setState(224);
					queryFuctionCall();
					}
					break;
				}
				setState(228);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__15) | (1L << T__16) | (1L << T__17) | (1L << T__18))) != 0)) {
					{
					setState(227);
					repetitionAmount();
					}
				}

				}
				}
				break;
			case T__9:
				enterOuterAlt(_localctx, 2);
				{
				setState(230);
				match(T__9);
				setState(231);
				sequencePartNoCapture();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class QueryFuctionCallContext extends ParserRuleContext {
		public TerminalNode NAME() { return getToken(cqlParser.NAME, 0); }
		public CommaSeparatedParamListContext commaSeparatedParamList() {
			return getRuleContext(CommaSeparatedParamListContext.class,0);
		}
		public QueryFuctionCallContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_queryFuctionCall; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterQueryFuctionCall(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitQueryFuctionCall(this);
		}
	}

	public final QueryFuctionCallContext queryFuctionCall() throws RecognitionException {
		QueryFuctionCallContext _localctx = new QueryFuctionCallContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_queryFuctionCall);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(234);
			match(NAME);
			setState(235);
			match(T__7);
			setState(236);
			commaSeparatedParamList();
			setState(237);
			match(T__8);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class CommaSeparatedParamListContext extends ParserRuleContext {
		public List<FunctionParamContext> functionParam() {
			return getRuleContexts(FunctionParamContext.class);
		}
		public FunctionParamContext functionParam(int i) {
			return getRuleContext(FunctionParamContext.class,i);
		}
		public CommaSeparatedParamListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_commaSeparatedParamList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterCommaSeparatedParamList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitCommaSeparatedParamList(this);
		}
	}

	public final CommaSeparatedParamListContext commaSeparatedParamList() throws RecognitionException {
		CommaSeparatedParamListContext _localctx = new CommaSeparatedParamListContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_commaSeparatedParamList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(239);
			functionParam();
			setState(244);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__20) {
				{
				{
				setState(240);
				match(T__20);
				setState(241);
				functionParam();
				}
				}
				setState(246);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FunctionParamContext extends ParserRuleContext {
		public ConstrainedQueryContext constrainedQuery() {
			return getRuleContext(ConstrainedQueryContext.class,0);
		}
		public FunctionParamContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionParam; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterFunctionParam(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitFunctionParam(this);
		}
	}

	public final FunctionParamContext functionParam() throws RecognitionException {
		FunctionParamContext _localctx = new FunctionParamContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_functionParam);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(247);
			constrainedQuery();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class CaptureLabelContext extends ParserRuleContext {
		public TerminalNode NAME() { return getToken(cqlParser.NAME, 0); }
		public TerminalNode NUMBER() { return getToken(cqlParser.NUMBER, 0); }
		public CaptureLabelContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_captureLabel; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterCaptureLabel(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitCaptureLabel(this);
		}
	}

	public final CaptureLabelContext captureLabel() throws RecognitionException {
		CaptureLabelContext _localctx = new CaptureLabelContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_captureLabel);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(249);
			_la = _input.LA(1);
			if ( !(_la==NAME || _la==NUMBER) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PositionContext extends ParserRuleContext {
		public PositionWordContext positionWord() {
			return getRuleContext(PositionWordContext.class,0);
		}
		public FlagsContext flags() {
			return getRuleContext(FlagsContext.class,0);
		}
		public TerminalNode DEFAULT_VALUE() { return getToken(cqlParser.DEFAULT_VALUE, 0); }
		public PositionLongContext positionLong() {
			return getRuleContext(PositionLongContext.class,0);
		}
		public PositionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_position; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterPosition(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitPosition(this);
		}
	}

	public final PositionContext position() throws RecognitionException {
		PositionContext _localctx = new PositionContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_position);
		int _la;
		try {
			setState(260);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case QUOTED_STRING:
			case SINGLE_QUOTED_STRING:
				enterOuterAlt(_localctx, 1);
				{
				setState(251);
				positionWord();
				setState(253);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==FLAGS) {
					{
					setState(252);
					flags();
					}
				}

				}
				break;
			case DEFAULT_VALUE:
				enterOuterAlt(_localctx, 2);
				{
				setState(255);
				match(DEFAULT_VALUE);
				}
				break;
			case T__7:
				enterOuterAlt(_localctx, 3);
				{
				setState(256);
				match(T__7);
				setState(257);
				positionLong();
				setState(258);
				match(T__8);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FlagsContext extends ParserRuleContext {
		public TerminalNode FLAGS() { return getToken(cqlParser.FLAGS, 0); }
		public FlagsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_flags; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterFlags(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitFlags(this);
		}
	}

	public final FlagsContext flags() throws RecognitionException {
		FlagsContext _localctx = new FlagsContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_flags);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(262);
			match(FLAGS);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PositionWordContext extends ParserRuleContext {
		public QuotedStringContext quotedString() {
			return getRuleContext(QuotedStringContext.class,0);
		}
		public PositionWordContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_positionWord; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterPositionWord(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitPositionWord(this);
		}
	}

	public final PositionWordContext positionWord() throws RecognitionException {
		PositionWordContext _localctx = new PositionWordContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_positionWord);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(264);
			quotedString();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PositionLongContext extends ParserRuleContext {
		public PositionLongPartContext positionLongPart() {
			return getRuleContext(PositionLongPartContext.class,0);
		}
		public BooleanOperatorContext booleanOperator() {
			return getRuleContext(BooleanOperatorContext.class,0);
		}
		public PositionLongContext positionLong() {
			return getRuleContext(PositionLongContext.class,0);
		}
		public PositionLongContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_positionLong; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterPositionLong(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitPositionLong(this);
		}
	}

	public final PositionLongContext positionLong() throws RecognitionException {
		PositionLongContext _localctx = new PositionLongContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_positionLong);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(266);
			positionLongPart();
			setState(270);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__21) | (1L << T__22) | (1L << T__23))) != 0)) {
				{
				setState(267);
				booleanOperator();
				setState(268);
				positionLong();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PositionLongPartContext extends ParserRuleContext {
		public AttValuePairContext attValuePair() {
			return getRuleContext(AttValuePairContext.class,0);
		}
		public PositionLongContext positionLong() {
			return getRuleContext(PositionLongContext.class,0);
		}
		public PositionLongPartContext positionLongPart() {
			return getRuleContext(PositionLongPartContext.class,0);
		}
		public PositionLongPartContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_positionLongPart; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterPositionLongPart(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitPositionLongPart(this);
		}
	}

	public final PositionLongPartContext positionLongPart() throws RecognitionException {
		PositionLongPartContext _localctx = new PositionLongPartContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_positionLongPart);
		try {
			setState(279);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NAME:
				enterOuterAlt(_localctx, 1);
				{
				setState(272);
				attValuePair();
				}
				break;
			case T__7:
				enterOuterAlt(_localctx, 2);
				{
				setState(273);
				match(T__7);
				setState(274);
				positionLong();
				setState(275);
				match(T__8);
				}
				break;
			case T__9:
				enterOuterAlt(_localctx, 3);
				{
				setState(277);
				match(T__9);
				setState(278);
				positionLongPart();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AttValuePairContext extends ParserRuleContext {
		public AnnotNameContext annotName() {
			return getRuleContext(AnnotNameContext.class,0);
		}
		public ValuePartContext valuePart() {
			return getRuleContext(ValuePartContext.class,0);
		}
		public FlagsContext flags() {
			return getRuleContext(FlagsContext.class,0);
		}
		public AttValuePairContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_attValuePair; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterAttValuePair(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitAttValuePair(this);
		}
	}

	public final AttValuePairContext attValuePair() throws RecognitionException {
		AttValuePairContext _localctx = new AttValuePairContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_attValuePair);
		int _la;
		try {
			setState(293);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,30,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(281);
				annotName();
				setState(282);
				match(T__1);
				setState(283);
				valuePart();
				setState(285);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==FLAGS) {
					{
					setState(284);
					flags();
					}
				}

				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(287);
				annotName();
				setState(288);
				match(T__2);
				setState(289);
				valuePart();
				setState(291);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==FLAGS) {
					{
					setState(290);
					flags();
					}
				}

				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AnnotNameContext extends ParserRuleContext {
		public List<TerminalNode> NAME() { return getTokens(cqlParser.NAME); }
		public TerminalNode NAME(int i) {
			return getToken(cqlParser.NAME, i);
		}
		public AnnotNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_annotName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterAnnotName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitAnnotName(this);
		}
	}

	public final AnnotNameContext annotName() throws RecognitionException {
		AnnotNameContext _localctx = new AnnotNameContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_annotName);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(295);
			match(NAME);
			setState(298);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__24) {
				{
				setState(296);
				match(T__24);
				setState(297);
				match(NAME);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ValuePartContext extends ParserRuleContext {
		public QuotedStringContext quotedString() {
			return getRuleContext(QuotedStringContext.class,0);
		}
		public ValuePartContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_valuePart; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).enterValuePart(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof cqlListener ) ((cqlListener)listener).exitValuePart(this);
		}
	}

	public final ValuePartContext valuePart() throws RecognitionException {
		ValuePartContext _localctx = new ValuePartContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_valuePart);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(300);
			quotedString();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3,\u0131\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\3\2\3\2\3\2\3\3\3\3\5\3L\n\3\3\4\3\4\3\4\5\4Q\n\4\3"+
		"\5\3\5\3\5\3\5\5\5W\n\5\3\6\3\6\3\6\3\6\5\6]\n\6\3\7\3\7\3\b\3\b\3\b\3"+
		"\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\5\bp\n\b\5\br\n\b\3\t\3"+
		"\t\3\t\3\t\7\tx\n\t\f\t\16\t{\13\t\5\t}\n\t\3\t\5\t\u0080\n\t\3\n\3\n"+
		"\3\n\5\n\u0085\n\n\3\n\3\n\3\n\3\13\3\13\3\f\3\f\3\f\5\f\u008f\n\f\3\f"+
		"\3\f\3\f\3\r\3\r\3\r\7\r\u0097\n\r\f\r\16\r\u009a\13\r\3\r\3\r\3\r\3\r"+
		"\7\r\u00a0\n\r\f\r\16\r\u00a3\13\r\3\r\3\r\3\r\3\r\3\r\3\r\5\r\u00ab\n"+
		"\r\3\16\3\16\3\17\3\17\3\17\3\17\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3"+
		"\20\3\20\3\20\5\20\u00bd\n\20\3\20\5\20\u00c0\n\20\3\21\3\21\3\21\3\21"+
		"\5\21\u00c6\n\21\3\22\3\22\3\23\3\23\3\23\3\23\5\23\u00ce\n\23\3\24\3"+
		"\24\3\25\3\25\5\25\u00d4\n\25\3\26\3\26\3\26\3\26\3\26\5\26\u00db\n\26"+
		"\3\27\3\27\3\27\3\27\3\27\3\27\3\27\5\27\u00e4\n\27\3\27\5\27\u00e7\n"+
		"\27\3\27\3\27\5\27\u00eb\n\27\3\30\3\30\3\30\3\30\3\30\3\31\3\31\3\31"+
		"\7\31\u00f5\n\31\f\31\16\31\u00f8\13\31\3\32\3\32\3\33\3\33\3\34\3\34"+
		"\5\34\u0100\n\34\3\34\3\34\3\34\3\34\3\34\5\34\u0107\n\34\3\35\3\35\3"+
		"\36\3\36\3\37\3\37\3\37\3\37\5\37\u0111\n\37\3 \3 \3 \3 \3 \3 \3 \5 \u011a"+
		"\n \3!\3!\3!\3!\5!\u0120\n!\3!\3!\3!\3!\5!\u0126\n!\5!\u0128\n!\3\"\3"+
		"\"\3\"\5\"\u012d\n\"\3#\3#\3#\2\2$\2\4\6\b\n\f\16\20\22\24\26\30\32\34"+
		"\36 \"$&(*,.\60\62\64\668:<>@BD\2\b\3\2\4\t\3\2$%\3\2+,\3\2 !\3\2\30\32"+
		"\4\2&&((\2\u0138\2F\3\2\2\2\4K\3\2\2\2\6M\3\2\2\2\bR\3\2\2\2\nX\3\2\2"+
		"\2\f^\3\2\2\2\16q\3\2\2\2\20\177\3\2\2\2\22\u0084\3\2\2\2\24\u0089\3\2"+
		"\2\2\26\u008e\3\2\2\2\30\u00aa\3\2\2\2\32\u00ac\3\2\2\2\34\u00ae\3\2\2"+
		"\2\36\u00bf\3\2\2\2 \u00c1\3\2\2\2\"\u00c7\3\2\2\2$\u00c9\3\2\2\2&\u00cf"+
		"\3\2\2\2(\u00d1\3\2\2\2*\u00da\3\2\2\2,\u00ea\3\2\2\2.\u00ec\3\2\2\2\60"+
		"\u00f1\3\2\2\2\62\u00f9\3\2\2\2\64\u00fb\3\2\2\2\66\u0106\3\2\2\28\u0108"+
		"\3\2\2\2:\u010a\3\2\2\2<\u010c\3\2\2\2>\u0119\3\2\2\2@\u0127\3\2\2\2B"+
		"\u0129\3\2\2\2D\u012e\3\2\2\2FG\5\4\3\2GH\7\2\2\3H\3\3\2\2\2IL\7)\2\2"+
		"JL\5\6\4\2KI\3\2\2\2KJ\3\2\2\2L\5\3\2\2\2MP\5\20\t\2NO\7\3\2\2OQ\5\b\5"+
		"\2PN\3\2\2\2PQ\3\2\2\2Q\7\3\2\2\2RV\5\n\6\2ST\5&\24\2TU\5\b\5\2UW\3\2"+
		"\2\2VS\3\2\2\2VW\3\2\2\2W\t\3\2\2\2X\\\5\16\b\2YZ\5\f\7\2Z[\5\16\b\2["+
		"]\3\2\2\2\\Y\3\2\2\2\\]\3\2\2\2]\13\3\2\2\2^_\t\2\2\2_\r\3\2\2\2`r\5\32"+
		"\16\2ab\7\n\2\2bc\5\b\5\2cd\7\13\2\2dr\3\2\2\2ef\7\f\2\2fr\5\16\b\2gh"+
		"\7&\2\2hi\7\n\2\2ij\5\64\33\2jk\7\13\2\2kr\3\2\2\2lo\5\64\33\2mn\7\r\2"+
		"\2np\7&\2\2om\3\2\2\2op\3\2\2\2pr\3\2\2\2q`\3\2\2\2qa\3\2\2\2qe\3\2\2"+
		"\2qg\3\2\2\2ql\3\2\2\2r\17\3\2\2\2s|\5 \21\2ty\5\22\n\2uv\7\16\2\2vx\5"+
		"\22\n\2wu\3\2\2\2x{\3\2\2\2yw\3\2\2\2yz\3\2\2\2z}\3\2\2\2{y\3\2\2\2|t"+
		"\3\2\2\2|}\3\2\2\2}\u0080\3\2\2\2~\u0080\5\26\f\2\177s\3\2\2\2\177~\3"+
		"\2\2\2\u0080\21\3\2\2\2\u0081\u0082\5\64\33\2\u0082\u0083\7\17\2\2\u0083"+
		"\u0085\3\2\2\2\u0084\u0081\3\2\2\2\u0084\u0085\3\2\2\2\u0085\u0086\3\2"+
		"\2\2\u0086\u0087\5\24\13\2\u0087\u0088\5\20\t\2\u0088\23\3\2\2\2\u0089"+
		"\u008a\t\3\2\2\u008a\25\3\2\2\2\u008b\u008c\5\64\33\2\u008c\u008d\7\17"+
		"\2\2\u008d\u008f\3\2\2\2\u008e\u008b\3\2\2\2\u008e\u008f\3\2\2\2\u008f"+
		"\u0090\3\2\2\2\u0090\u0091\7#\2\2\u0091\u0092\5\20\t\2\u0092\27\3\2\2"+
		"\2\u0093\u0094\7\t\2\2\u0094\u0098\7&\2\2\u0095\u0097\5\34\17\2\u0096"+
		"\u0095\3\2\2\2\u0097\u009a\3\2\2\2\u0098\u0096\3\2\2\2\u0098\u0099\3\2"+
		"\2\2\u0099\u009b\3\2\2\2\u009a\u0098\3\2\2\2\u009b\u00ab\7\20\2\2\u009c"+
		"\u009d\7\t\2\2\u009d\u00a1\7&\2\2\u009e\u00a0\5\34\17\2\u009f\u009e\3"+
		"\2\2\2\u00a0\u00a3\3\2\2\2\u00a1\u009f\3\2\2\2\u00a1\u00a2\3\2\2\2\u00a2"+
		"\u00a4\3\2\2\2\u00a3\u00a1\3\2\2\2\u00a4\u00a5\7\b\2\2\u00a5\u00a6\5\2"+
		"\2\2\u00a6\u00a7\7\21\2\2\u00a7\u00a8\7&\2\2\u00a8\u00a9\7\b\2\2\u00a9"+
		"\u00ab\3\2\2\2\u00aa\u0093\3\2\2\2\u00aa\u009c\3\2\2\2\u00ab\31\3\2\2"+
		"\2\u00ac\u00ad\t\4\2\2\u00ad\33\3\2\2\2\u00ae\u00af\7&\2\2\u00af\u00b0"+
		"\7\4\2\2\u00b0\u00b1\5\32\16\2\u00b1\35\3\2\2\2\u00b2\u00c0\7\22\2\2\u00b3"+
		"\u00c0\7\23\2\2\u00b4\u00c0\7\24\2\2\u00b5\u00b6\7\25\2\2\u00b6\u00b7"+
		"\7(\2\2\u00b7\u00c0\7\26\2\2\u00b8\u00b9\7\25\2\2\u00b9\u00ba\7(\2\2\u00ba"+
		"\u00bc\7\27\2\2\u00bb\u00bd\7(\2\2\u00bc\u00bb\3\2\2\2\u00bc\u00bd\3\2"+
		"\2\2\u00bd\u00be\3\2\2\2\u00be\u00c0\7\26\2\2\u00bf\u00b2\3\2\2\2\u00bf"+
		"\u00b3\3\2\2\2\u00bf\u00b4\3\2\2\2\u00bf\u00b5\3\2\2\2\u00bf\u00b8\3\2"+
		"\2\2\u00c0\37\3\2\2\2\u00c1\u00c5\5$\23\2\u00c2\u00c3\5\"\22\2\u00c3\u00c4"+
		"\5 \21\2\u00c4\u00c6\3\2\2\2\u00c5\u00c2\3\2\2\2\u00c5\u00c6\3\2\2\2\u00c6"+
		"!\3\2\2\2\u00c7\u00c8\t\5\2\2\u00c8#\3\2\2\2\u00c9\u00cd\5(\25\2\u00ca"+
		"\u00cb\5&\24\2\u00cb\u00cc\5$\23\2\u00cc\u00ce\3\2\2\2\u00cd\u00ca\3\2"+
		"\2\2\u00cd\u00ce\3\2\2\2\u00ce%\3\2\2\2\u00cf\u00d0\t\6\2\2\u00d0\'\3"+
		"\2\2\2\u00d1\u00d3\5*\26\2\u00d2\u00d4\5(\25\2\u00d3\u00d2\3\2\2\2\u00d3"+
		"\u00d4\3\2\2\2\u00d4)\3\2\2\2\u00d5\u00db\5,\27\2\u00d6\u00d7\5\64\33"+
		"\2\u00d7\u00d8\7\17\2\2\u00d8\u00d9\5,\27\2\u00d9\u00db\3\2\2\2\u00da"+
		"\u00d5\3\2\2\2\u00da\u00d6\3\2\2\2\u00db+\3\2\2\2\u00dc\u00e4\5\30\r\2"+
		"\u00dd\u00e4\5\66\34\2\u00de\u00df\7\n\2\2\u00df\u00e0\5\6\4\2\u00e0\u00e1"+
		"\7\13\2\2\u00e1\u00e4\3\2\2\2\u00e2\u00e4\5.\30\2\u00e3\u00dc\3\2\2\2"+
		"\u00e3\u00dd\3\2\2\2\u00e3\u00de\3\2\2\2\u00e3\u00e2\3\2\2\2\u00e4\u00e6"+
		"\3\2\2\2\u00e5\u00e7\5\36\20\2\u00e6\u00e5\3\2\2\2\u00e6\u00e7\3\2\2\2"+
		"\u00e7\u00eb\3\2\2\2\u00e8\u00e9\7\f\2\2\u00e9\u00eb\5,\27\2\u00ea\u00e3"+
		"\3\2\2\2\u00ea\u00e8\3\2\2\2\u00eb-\3\2\2\2\u00ec\u00ed\7&\2\2\u00ed\u00ee"+
		"\7\n\2\2\u00ee\u00ef\5\60\31\2\u00ef\u00f0\7\13\2\2\u00f0/\3\2\2\2\u00f1"+
		"\u00f6\5\62\32\2\u00f2\u00f3\7\27\2\2\u00f3\u00f5\5\62\32\2\u00f4\u00f2"+
		"\3\2\2\2\u00f5\u00f8\3\2\2\2\u00f6\u00f4\3\2\2\2\u00f6\u00f7\3\2\2\2\u00f7"+
		"\61\3\2\2\2\u00f8\u00f6\3\2\2\2\u00f9\u00fa\5\6\4\2\u00fa\63\3\2\2\2\u00fb"+
		"\u00fc\t\7\2\2\u00fc\65\3\2\2\2\u00fd\u00ff\5:\36\2\u00fe\u0100\58\35"+
		"\2\u00ff\u00fe\3\2\2\2\u00ff\u0100\3\2\2\2\u0100\u0107\3\2\2\2\u0101\u0107"+
		"\7\"\2\2\u0102\u0103\7\n\2\2\u0103\u0104\5<\37\2\u0104\u0105\7\13\2\2"+
		"\u0105\u0107\3\2\2\2\u0106\u00fd\3\2\2\2\u0106\u0101\3\2\2\2\u0106\u0102"+
		"\3\2\2\2\u0107\67\3\2\2\2\u0108\u0109\7\'\2\2\u01099\3\2\2\2\u010a\u010b"+
		"\5\32\16\2\u010b;\3\2\2\2\u010c\u0110\5> \2\u010d\u010e\5&\24\2\u010e"+
		"\u010f\5<\37\2\u010f\u0111\3\2\2\2\u0110\u010d\3\2\2\2\u0110\u0111\3\2"+
		"\2\2\u0111=\3\2\2\2\u0112\u011a\5@!\2\u0113\u0114\7\n\2\2\u0114\u0115"+
		"\5<\37\2\u0115\u0116\7\13\2\2\u0116\u011a\3\2\2\2\u0117\u0118\7\f\2\2"+
		"\u0118\u011a\5> \2\u0119\u0112\3\2\2\2\u0119\u0113\3\2\2\2\u0119\u0117"+
		"\3\2\2\2\u011a?\3\2\2\2\u011b\u011c\5B\"\2\u011c\u011d\7\4\2\2\u011d\u011f"+
		"\5D#\2\u011e\u0120\58\35\2\u011f\u011e\3\2\2\2\u011f\u0120\3\2\2\2\u0120"+
		"\u0128\3\2\2\2\u0121\u0122\5B\"\2\u0122\u0123\7\5\2\2\u0123\u0125\5D#"+
		"\2\u0124\u0126\58\35\2\u0125\u0124\3\2\2\2\u0125\u0126\3\2\2\2\u0126\u0128"+
		"\3\2\2\2\u0127\u011b\3\2\2\2\u0127\u0121\3\2\2\2\u0128A\3\2\2\2\u0129"+
		"\u012c\7&\2\2\u012a\u012b\7\33\2\2\u012b\u012d\7&\2\2\u012c\u012a\3\2"+
		"\2\2\u012c\u012d\3\2\2\2\u012dC\3\2\2\2\u012e\u012f\5\32\16\2\u012fE\3"+
		"\2\2\2\"KPV\\oqy|\177\u0084\u008e\u0098\u00a1\u00aa\u00bc\u00bf\u00c5"+
		"\u00cd\u00d3\u00da\u00e3\u00e6\u00ea\u00f6\u00ff\u0106\u0110\u0119\u011f"+
		"\u0125\u0127\u012c";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}