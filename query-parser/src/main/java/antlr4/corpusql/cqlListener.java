// Generated from corpusql\cql.g4 by ANTLR 4.9.3
package corpusql;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link cqlParser}.
 */
public interface cqlListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link cqlParser#textpattern}.
	 * @param ctx the parse tree
	 */
	void enterTextpattern(cqlParser.TextpatternContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#textpattern}.
	 * @param ctx the parse tree
	 */
	void exitTextpattern(cqlParser.TextpatternContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#settingsQuery}.
	 * @param ctx the parse tree
	 */
	void enterSettingsQuery(cqlParser.SettingsQueryContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#settingsQuery}.
	 * @param ctx the parse tree
	 */
	void exitSettingsQuery(cqlParser.SettingsQueryContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#constrainedQuery}.
	 * @param ctx the parse tree
	 */
	void enterConstrainedQuery(cqlParser.ConstrainedQueryContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#constrainedQuery}.
	 * @param ctx the parse tree
	 */
	void exitConstrainedQuery(cqlParser.ConstrainedQueryContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#constraint}.
	 * @param ctx the parse tree
	 */
	void enterConstraint(cqlParser.ConstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#constraint}.
	 * @param ctx the parse tree
	 */
	void exitConstraint(cqlParser.ConstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#simpleConstraint}.
	 * @param ctx the parse tree
	 */
	void enterSimpleConstraint(cqlParser.SimpleConstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#simpleConstraint}.
	 * @param ctx the parse tree
	 */
	void exitSimpleConstraint(cqlParser.SimpleConstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#comparisonOperator}.
	 * @param ctx the parse tree
	 */
	void enterComparisonOperator(cqlParser.ComparisonOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#comparisonOperator}.
	 * @param ctx the parse tree
	 */
	void exitComparisonOperator(cqlParser.ComparisonOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#constraintValue}.
	 * @param ctx the parse tree
	 */
	void enterConstraintValue(cqlParser.ConstraintValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#constraintValue}.
	 * @param ctx the parse tree
	 */
	void exitConstraintValue(cqlParser.ConstraintValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#relationQuery}.
	 * @param ctx the parse tree
	 */
	void enterRelationQuery(cqlParser.RelationQueryContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#relationQuery}.
	 * @param ctx the parse tree
	 */
	void exitRelationQuery(cqlParser.RelationQueryContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#childRelation}.
	 * @param ctx the parse tree
	 */
	void enterChildRelation(cqlParser.ChildRelationContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#childRelation}.
	 * @param ctx the parse tree
	 */
	void exitChildRelation(cqlParser.ChildRelationContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#relationType}.
	 * @param ctx the parse tree
	 */
	void enterRelationType(cqlParser.RelationTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#relationType}.
	 * @param ctx the parse tree
	 */
	void exitRelationType(cqlParser.RelationTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#rootRelationType}.
	 * @param ctx the parse tree
	 */
	void enterRootRelationType(cqlParser.RootRelationTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#rootRelationType}.
	 * @param ctx the parse tree
	 */
	void exitRootRelationType(cqlParser.RootRelationTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#tag}.
	 * @param ctx the parse tree
	 */
	void enterTag(cqlParser.TagContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#tag}.
	 * @param ctx the parse tree
	 */
	void exitTag(cqlParser.TagContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#quotedString}.
	 * @param ctx the parse tree
	 */
	void enterQuotedString(cqlParser.QuotedStringContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#quotedString}.
	 * @param ctx the parse tree
	 */
	void exitQuotedString(cqlParser.QuotedStringContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#attributes}.
	 * @param ctx the parse tree
	 */
	void enterAttributes(cqlParser.AttributesContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#attributes}.
	 * @param ctx the parse tree
	 */
	void exitAttributes(cqlParser.AttributesContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#repetitionAmount}.
	 * @param ctx the parse tree
	 */
	void enterRepetitionAmount(cqlParser.RepetitionAmountContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#repetitionAmount}.
	 * @param ctx the parse tree
	 */
	void exitRepetitionAmount(cqlParser.RepetitionAmountContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#complexQuery}.
	 * @param ctx the parse tree
	 */
	void enterComplexQuery(cqlParser.ComplexQueryContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#complexQuery}.
	 * @param ctx the parse tree
	 */
	void exitComplexQuery(cqlParser.ComplexQueryContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#queryOperator}.
	 * @param ctx the parse tree
	 */
	void enterQueryOperator(cqlParser.QueryOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#queryOperator}.
	 * @param ctx the parse tree
	 */
	void exitQueryOperator(cqlParser.QueryOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#simpleQuery}.
	 * @param ctx the parse tree
	 */
	void enterSimpleQuery(cqlParser.SimpleQueryContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#simpleQuery}.
	 * @param ctx the parse tree
	 */
	void exitSimpleQuery(cqlParser.SimpleQueryContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#booleanOperator}.
	 * @param ctx the parse tree
	 */
	void enterBooleanOperator(cqlParser.BooleanOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#booleanOperator}.
	 * @param ctx the parse tree
	 */
	void exitBooleanOperator(cqlParser.BooleanOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#sequence}.
	 * @param ctx the parse tree
	 */
	void enterSequence(cqlParser.SequenceContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#sequence}.
	 * @param ctx the parse tree
	 */
	void exitSequence(cqlParser.SequenceContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#captureQuery}.
	 * @param ctx the parse tree
	 */
	void enterCaptureQuery(cqlParser.CaptureQueryContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#captureQuery}.
	 * @param ctx the parse tree
	 */
	void exitCaptureQuery(cqlParser.CaptureQueryContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#sequencePartNoCapture}.
	 * @param ctx the parse tree
	 */
	void enterSequencePartNoCapture(cqlParser.SequencePartNoCaptureContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#sequencePartNoCapture}.
	 * @param ctx the parse tree
	 */
	void exitSequencePartNoCapture(cqlParser.SequencePartNoCaptureContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#queryFuctionCall}.
	 * @param ctx the parse tree
	 */
	void enterQueryFuctionCall(cqlParser.QueryFuctionCallContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#queryFuctionCall}.
	 * @param ctx the parse tree
	 */
	void exitQueryFuctionCall(cqlParser.QueryFuctionCallContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#commaSeparatedParamList}.
	 * @param ctx the parse tree
	 */
	void enterCommaSeparatedParamList(cqlParser.CommaSeparatedParamListContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#commaSeparatedParamList}.
	 * @param ctx the parse tree
	 */
	void exitCommaSeparatedParamList(cqlParser.CommaSeparatedParamListContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#functionParam}.
	 * @param ctx the parse tree
	 */
	void enterFunctionParam(cqlParser.FunctionParamContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#functionParam}.
	 * @param ctx the parse tree
	 */
	void exitFunctionParam(cqlParser.FunctionParamContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#captureLabel}.
	 * @param ctx the parse tree
	 */
	void enterCaptureLabel(cqlParser.CaptureLabelContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#captureLabel}.
	 * @param ctx the parse tree
	 */
	void exitCaptureLabel(cqlParser.CaptureLabelContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#position}.
	 * @param ctx the parse tree
	 */
	void enterPosition(cqlParser.PositionContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#position}.
	 * @param ctx the parse tree
	 */
	void exitPosition(cqlParser.PositionContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#flags}.
	 * @param ctx the parse tree
	 */
	void enterFlags(cqlParser.FlagsContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#flags}.
	 * @param ctx the parse tree
	 */
	void exitFlags(cqlParser.FlagsContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#positionWord}.
	 * @param ctx the parse tree
	 */
	void enterPositionWord(cqlParser.PositionWordContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#positionWord}.
	 * @param ctx the parse tree
	 */
	void exitPositionWord(cqlParser.PositionWordContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#positionLong}.
	 * @param ctx the parse tree
	 */
	void enterPositionLong(cqlParser.PositionLongContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#positionLong}.
	 * @param ctx the parse tree
	 */
	void exitPositionLong(cqlParser.PositionLongContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#positionLongPart}.
	 * @param ctx the parse tree
	 */
	void enterPositionLongPart(cqlParser.PositionLongPartContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#positionLongPart}.
	 * @param ctx the parse tree
	 */
	void exitPositionLongPart(cqlParser.PositionLongPartContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#attValuePair}.
	 * @param ctx the parse tree
	 */
	void enterAttValuePair(cqlParser.AttValuePairContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#attValuePair}.
	 * @param ctx the parse tree
	 */
	void exitAttValuePair(cqlParser.AttValuePairContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#annotName}.
	 * @param ctx the parse tree
	 */
	void enterAnnotName(cqlParser.AnnotNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#annotName}.
	 * @param ctx the parse tree
	 */
	void exitAnnotName(cqlParser.AnnotNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link cqlParser#valuePart}.
	 * @param ctx the parse tree
	 */
	void enterValuePart(cqlParser.ValuePartContext ctx);
	/**
	 * Exit a parse tree produced by {@link cqlParser#valuePart}.
	 * @param ctx the parse tree
	 */
	void exitValuePart(cqlParser.ValuePartContext ctx);
}