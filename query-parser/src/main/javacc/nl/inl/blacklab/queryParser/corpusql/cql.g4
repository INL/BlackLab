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

// disambiguated for query editor autocompletion purposes
fragment DEP_NAME : (~[\-] ((~[\-] | '-' ~[>]))*);
fragment ALIGNMENT_NAME : (~[=] ((~[=] | '=' ~[>]))*);
fragment DEP_TARGET: ([A-Za-z_\-0-9])*;
fragment ALIGNMENT_TARGET: DEP_TARGET;

WHITESPACE: [ \t\r\n]+ -> skip ;
SINGLE_LINE_COMMENT: '#' ~[\r\n]* -> channel(HIDDEN);
MULTI_LINE_COMMENT: '/*' .*? '*/' -> channel(HIDDEN);

WITHIN          : W I T H I N;
CONTAINING      : C O N T A I N I N G;

DEFAULT_VALUE   : '_';

ROOT_DEP_OP : '^-' DEP_NAME? '->' DEP_TARGET;
DEP_OP : ('!')? '-' DEP_NAME? '->' DEP_TARGET;
ALIGNMENT_OP : '=' ALIGNMENT_NAME? '=>' ALIGNMENT_TARGET;


NAME            : [a-zA-Z_] [a-zA-Z_\-0-9]*;
FLAGS           : '%' (C | D | L)+;
NUMBER          : [0-9]+;
SETTINGS_OP     : '@' NAME '=' NAME (',' NAME '=' NAME)*;
SETTINGS        : SETTINGS_OP (',' SETTINGS_OP)*;
QUOTED_STRING   : '"' (~["\\] | '\\' . )* '"';
SINGLE_QUOTED_STRING: '\'' (~["\\] | '\\' . )* '\'';

// we need to match anything we want to use in syntax highlighting
// and we also can't group them together, because then we can't distinguish them in the parser
// string literals in the parser (e.g. '=') work as long as they exactly match the token
// so there's no need to replace all of them with the token name.
LBRACKET        : '[';
RBRACKET        : ']';
EQUALS          : '=';
NOT_EQUALS      : '!=';
GREATER_THAN    : '>';
LESS_THAN       : '<';
GREATER_THAN_OR_EQUAL: '>=';
LESS_THAN_OR_EQUAL: '<=';
AND             : '&';
OR              : '|';
NOT             : '!';
COLON           : ':';
STAR            : '*';
PLUS            : '+';
QUESTION        : '?';
COMMA           : ',';
SEMICOLON       : ';';



/*
 * Parser Rules
 */

query: settingsQuery EOF;
settingsQuery: SETTINGS_OP | constrainedQuery;
constrainedQuery: relationQuery ('::' constraint)?;
constraint: simpleConstraint (booleanOperator constraint)?;
simpleConstraint: constraintValue (comparisonOperator constraintValue)?;
comparisonOperator: '=' | '!=' | '>=' | '<=' | '>' | '<';
//comparisonOperator: EQUALS | NOT_EQUALS | GREATER_THAN_OR_EQUAL | LESS_THAN_OR_EQUAL | GREATER_THAN | LESS_THAN;
constraintValue: quotedString | '(' constraint ')' | '!' constraintValue | NAME '(' captureLabel ')' | captureLabel ('.' NAME)?;
relationQuery: complexQuery (childRelation (';' childRelation)*)? | rootRelationType;
childRelation: (captureLabel ':')? relationType relationQuery;
relationType: DEP_OP | ALIGNMENT_OP;
rootRelationType: (captureLabel ':')? ROOT_DEP_OP relationQuery;
tag: '<' tagName attribute* '/>' | '<' tagName attribute* '>' query '</' tagName '>';
quotedString: QUOTED_STRING | SINGLE_QUOTED_STRING;
attribute: attributeName '=' attributeValue;
repetitionAmount: '*' | '+' | '?' | '{' NUMBER '}' | '{' NUMBER ',' NUMBER? '}';
complexQuery: simpleQuery (queryOperator complexQuery)?;
queryOperator: WITHIN | CONTAINING;
simpleQuery: sequence (booleanOperator simpleQuery)?;
booleanOperator: '&' | '|' | '->';
sequence: captureQuery+;
captureQuery: sequencePartNoCapture | captureLabel ':' sequencePartNoCapture;
sequencePartNoCapture: ( tag | position | '(' constrainedQuery ')' | queryFuctionCall ) repetitionAmount? | '!' sequencePartNoCapture;
queryFuctionCall: NAME '(' commaSeparatedParamList ')';
commaSeparatedParamList: functionParam (',' functionParam)*;
functionParam: constrainedQuery;
captureLabel: NAME | NUMBER;
// the main token matcher, e.g. "word", _, or [att="value"], etc.
position: positionWord flags? | DEFAULT_VALUE | '[' positionLong? ']';
flags: FLAGS;
positionWord: quotedString;
positionLong: positionLongPart (booleanOperator positionLong)?;
positionLongPart: attValuePair | '(' positionLong ')' | '!' positionLongPart;
attValuePair: annotName ('=' | '!=') valuePart flags?;
annotName: NAME ('/' NAME)?;
valuePart: quotedString;

// Some disambiguation between different kind of keys and values, so we can autocomplete better.
// (we only have access to the current token/rule, so items with distinct value possibilities need distinct names).
tagName: NAME;
attributeName: NAME;
attributeValue: quotedString;
