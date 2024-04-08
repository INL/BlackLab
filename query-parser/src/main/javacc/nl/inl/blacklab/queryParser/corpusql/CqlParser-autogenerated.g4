parser grammar CqlParser;

options { tokenVocab=CqlLexer; }

query : settingsQuery EOF  ;

settingsQuery : (SETTINGS_OP settingsQuery | constrainedQuery)  ;

constrainedQuery : relationQuery (CONSTRAINT_THING constraint)?  ;

constraint : simpleConstraint (booleanOperator constraint)?  ;

simpleConstraint : constraintValue (comparisonOperator constraintValue)?  ;

comparisonOperator : (EQUALS | NOT_EQUALS | GREATER_THAN_EQUALS | LESS_THAN_EQUALS | GREATER_THAN | LESS_THAN)  ;

constraintValue : (quotedString | LPAREN constraint RPAREN | NEGATE constraintValue | NAME LPAREN captureLabel RPAREN | captureLabel (DOT NAME)?)  ;

relationQuery : (complexQuery (childRelation (DELIMITER childRelation)*)? relationQuery | rootRelationType)  ;

childRelation : (captureLabel COLON)? relationType relationQuery  ;

relationType : (DEP_OP | ALIGNMENT_OP)  ;

rootRelationType : (captureLabel COLON)? ROOT_DEP_OP relationQuery  ;

tag : LESS_THAN FORWARD_SLASH? NAME attributes* FORWARD_SLASH? GREATER_THAN  ;

quotedString : (QUOTED_STRING | SINGLE_QUOTED_STRING)  ;

attributes : NAME EQUALS quotedString  ;

repetitionAmount : (MULT | PLUS | QUESTION | LBRACE NUMBER RBRACE | LBRACE NUMBER COMMA NUMBER? RBRACE)  ;

complexQuery : simpleQuery (queryOperator complexQuery)?  ;

queryOperator : (WITHIN | CONTAINING)  ;

simpleQuery : sequence (booleanOperator simpleQuery)?  ;

booleanOperator : (AND | OR | ARROW_RIGHT)  ;

sequence : captureQuery sequence?  ;

captureQuery : (sequencePartNoCapture | captureLabel COLON sequencePartNoCapture)  ;

sequencePartNoCapture : ((tag | position | LPAREN constrainedQuery RPAREN | queryFunctionCall) repetitionAmount? | NEGATE sequencePartNoCapture)  ;

queryFunctionCall : NAME LPAREN commaSeparatedParamList? RPAREN  ;

commaSeparatedParamList : functionParam (COMMA commaSeparatedParamList)?  ;

functionParam : constrainedQuery  ;

captureLabel : (NAME | NUMBER)  ;

position : (positionWord flags? | DEFAULT_VALUE | LBRACKET positionLong? RBRACKET)  ;

flags : FLAGS  ;

positionWord : quotedString  ;

positionLong : positionLongPart (booleanOperator positionLong)?  ;

positionLongPart : (attValuePair | LPAREN positionLong RPAREN | NEGATE positionLongPart)  ;

attValuePair : (annotName EQUALS valuePart flags? | annotName NOT_EQUALS valuePart flags?)  ;

annotName : NAME (FORWARD_SLASH NAME)?  ;

valuePart : quotedString  ;
