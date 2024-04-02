/*
 * Lexer Rules
 */

grammar cql;

fragment A : [aA]; // match either an 'a' or 'A'
fragment B : [bB];
fragment C : [cC];
fragment D : [dD];
fragment E : [eE];
fragment F : [fF];
fragment G : [gG];
fragment H : [hH];
fragment I : [iI];
fragment J : [jJ];
fragment K : [kK];
fragment L : [lL];
fragment M : [mM];
fragment N : [nN];
fragment O : [oO];
fragment P : [pP];
fragment Q : [qQ];
fragment R : [rR];
fragment S : [sS];
fragment T : [tT];
fragment U : [uU];
fragment V : [vV];
fragment W : [wW];
fragment X : [xX];
fragment Y : [yY];
fragment Z : [zZ];

fragment DEP_SEPARATOR: '-';
fragment ALIGNMENT_SEPARATOR: '=';
fragment DEP : ~[-];
fragment DEP_TARGET: [a-zA-Z0-9_]+;

WHITESPACE: [ \t\r\n]+ -> skip ;
SPECIAL_TOKEN: SINGLE_LINE_COMMENT | MULTI_LINE_COMMENT ;
    SINGLE_LINE_COMMENT: '#' ~[\r\n]* -> skip ;
    MULTI_LINE_COMMENT: '/*' .*? '*/' -> skip ;

WITHIN          : W I T H I N;
CONTAINING      : C O N T A I N I N G;

DEFAULT_VALUE   : '_';
ROOT_DEP_OP     : '^-' (DEP (DEP_SEPARATOR DEP)*)? '->' DEP_TARGET;
DEP_OP          : '!'? '-' (DEP (DEP_SEPARATOR DEP)*)? '->' DEP_TARGET;
ALIGNMENT_OP    : '=' (DEP (ALIGNMENT_SEPARATOR DEP)*)? '=>' DEP_TARGET;

NAME            : [a-zA-Z_] [a-zA-Z_\-0-9]*;
FLAGS           : '%' (C | D | L)+;
NUMBER          : [0-9]+;
SETTINGS_OP     : '@' NAME '=' NAME (',' NAME '=' NAME)*;
SETTINGS        : SETTINGS_OP (',' SETTINGS_OP)*;
QUOTED_STRING   : '"' (~["\\] | '\\' ~[] )* '"';
SINGLE_QUOTED_STRING: '\'' (~["\\] | '\\' ~[] )* '\'';


/*
 * Parser Rules
 */

textpattern: settingsQuery EOF;
settingsQuery: SETTINGS_OP | constrainedQuery;
constrainedQuery: relationQuery ('::' constraint)?;
constraint: simpleConstraint (booleanOperator constraint)?;
simpleConstraint: constraintValue (comparisonOperator constraintValue)?;
comparisonOperator: '=' | '!=' | '>=' | '<=' | '>' | '<';
constraintValue: quotedString | '(' constraint ')' | '!' constraintValue | NAME '(' captureLabel ')' | captureLabel ('.' NAME)?;
relationQuery: complexQuery (childRelation (';' childRelation)*)? | rootRelationType;
childRelation: (captureLabel ':')? relationType relationQuery;
relationType: DEP_OP | ALIGNMENT_OP;
rootRelationType: (captureLabel ':')? ROOT_DEP_OP relationQuery;
tag: '<' NAME attributes* '/>' | '<' NAME attributes* '>' textpattern '</' NAME '>';
quotedString: QUOTED_STRING | SINGLE_QUOTED_STRING;
attributes: NAME '=' quotedString;
repetitionAmount: '*' | '+' | '?' | '{' NUMBER '}' | '{' NUMBER ',' NUMBER? '}';
complexQuery: simpleQuery (queryOperator complexQuery)?;
queryOperator: WITHIN | CONTAINING;
simpleQuery: sequence (booleanOperator simpleQuery)?;
booleanOperator: '&' | '|' | '->';
sequence: captureQuery sequence?;
captureQuery: sequencePartNoCapture | captureLabel ':' sequencePartNoCapture;
sequencePartNoCapture: (( tag | position | '(' constrainedQuery ')' | queryFuctionCall ) repetitionAmount?) | '!' sequencePartNoCapture;
queryFuctionCall: NAME '(' commaSeparatedParamList ')';
commaSeparatedParamList: functionParam (',' functionParam)*;
functionParam: constrainedQuery;
captureLabel: NAME | NUMBER;
position: positionWord flags? | DEFAULT_VALUE | '(' positionLong ')';
flags: FLAGS;
positionWord: quotedString;
positionLong: positionLongPart (booleanOperator positionLong)?;
positionLongPart: attValuePair | '(' positionLong ')' | '!' positionLongPart;
attValuePair: annotName '=' valuePart flags? | annotName '!=' valuePart flags?;
annotName: NAME ('/' NAME)?;
valuePart: quotedString;

