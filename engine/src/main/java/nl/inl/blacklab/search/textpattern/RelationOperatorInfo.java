package nl.inl.blacklab.search.textpattern;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;

/**
 * Describes a relation operator, e.g. "-nmod->" or "==>".
 *
 * Include the relation type filter, relation direction (forward/backward, usually both;
 * direction also determines if it's a root relation operator), and whether it's a negation.
 *
 * For parallel corpora, it also includes source and target version (for parallel corpora, e.g. source
 * might be 'en' and target might be 'nl' to search from English to Dutch), and whether it's an
 * alignment operator ("find all relations between matches of these two queries") or a regular relation
 * operator ("find one matching relation").
 */
public class RelationOperatorInfo {

    /**
     * Relation or alignment operator, with optional source/target version and relation type regex.
     * Examples: --> ; -nmod-> ; nl==>de
     */
    private static final Pattern PATT_RELATION_OPERATOR = Pattern.compile(
            "^([a-zA-Z0-9_]*)[-=](.*)[-=]>([a-zA-Z0-9_]*)$");

    /**
     * Get the relation type and target version regexes from the operator.
     * NOTE: if the operator started with ! or ^, this character must have been removed already!
     * If no type was specified, the type will be ".*" (any relation type). If no target version was specified,
     * the target version will be the empty string (any target version or no target version).
     *
     * @param relationOperator    relation operator with optional type regex, e.g. "-det->" or "-det->de"
     * @param negate              is this a negated relation operator? E.g. !-nmod->
     * @param isAlignmentOperator is this an alignment operator? E.g. ==>
     * @return relation type and target version (intepret both as regexes)
     */
    private static RelationOperatorInfo parseRelationOperator(String relationOperator, boolean negate,
            boolean isAlignmentOperator, SpanQueryRelations.Direction direction) {
        Matcher matcher = PATT_RELATION_OPERATOR.matcher(relationOperator);
        if (!matcher.matches())
            throw new RuntimeException("Invalid relation operator: " + relationOperator);
        String sourceVersion = matcher.group(1);
        String typeRegex = matcher.group(2);
        String targetVersion = matcher.group(3);
        if (StringUtils.isEmpty(typeRegex))
            typeRegex = RelationUtil.ANY_TYPE_REGEX; // any relation type
        return new RelationOperatorInfo(typeRegex, direction, sourceVersion, targetVersion, negate,
                isAlignmentOperator);
    }

    /**
     * Create an info struct from the operator string.
     */
    public static RelationOperatorInfo fromOperator(String op) {
        boolean negate = false;
        boolean isRoot = op.charAt(0) == '^';
        if (isRoot)
            op = op.substring(1);
        boolean isAlignmentOperator = op.contains("=>");
        if (op.charAt(0) == '!') {
            negate = true;
            op = op.substring(1);
        }
        SpanQueryRelations.Direction direction = isRoot ? SpanQueryRelations.Direction.ROOT :
                SpanQueryRelations.Direction.BOTH_DIRECTIONS;
        return parseRelationOperator(op, negate, isAlignmentOperator, direction);
    }

    /**
     * Relation type regex
     */
    private final String typeRegex;

    private final SpanQueryRelations.Direction direction;

    /**
     * Source version we want to search the left side in
     */
    private final String sourceVersion;

    /**
     * Relation target regex
     */
    private final String targetVersion;

    /**
     * Is this a negated relation operator? E.g. !-nmod->
     */
    private final boolean negate;

    /**
     * Is this an alignment operator? E.g. ==>de ("find all alignment relations between spans on left and right")
     */
    private final boolean isAlignmentOperator;

    public RelationOperatorInfo(String typeRegex, SpanQueryRelations.Direction direction, String sourceVersion,
            String targetVersion, boolean negate,
            boolean isAlignmentOperator) {
        this.typeRegex = typeRegex;
        this.direction = direction;
        this.sourceVersion = sourceVersion == null || sourceVersion.isEmpty() ? null : sourceVersion;
        this.targetVersion = targetVersion == null || targetVersion.isEmpty() ? null : targetVersion;
        this.negate = negate;
        this.isAlignmentOperator = isAlignmentOperator;

        if (isAlignmentOperator && negate)
            throw new RuntimeException("Alignment operator cannot be negated");
    }

    public String getTypeRegex() {
        return typeRegex;
    }

    public SpanQueryRelations.Direction getDirection() {
        return direction;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public boolean isNegate() {
        return negate;
    }

    public Boolean isAlignment() {
        return isAlignmentOperator;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RelationOperatorInfo that = (RelationOperatorInfo) o;
        return negate == that.negate && isAlignmentOperator == that.isAlignmentOperator && Objects.equals(
                typeRegex, that.typeRegex) && direction == that.direction && Objects.equals(sourceVersion,
                that.sourceVersion) && Objects.equals(targetVersion, that.targetVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeRegex, direction, sourceVersion, targetVersion, negate, isAlignmentOperator);
    }

    //        /**
//         * Get the full relation type regex, optionally including the target version.
//         *
//         * @return
//         */
//        public String getFullTypeRegex() {
//            // Make sure our type regex has a relation class
//            String regex = RelationUtil.optPrependDefaultClass(typeRegex);
//            if (targetVersion == null || targetVersion.isEmpty())
//                return regex;
//            String[] classAndType = RelationUtil.classAndType(regex);
//            String relationClass = classAndType[0];
//            String relationType = classAndType[1];
//            // A target version was set. Target version must be added to or replaced in type regex.
//            // Replace or add target version in relation class
//            relationClass = AnnotatedFieldNameUtil.getParallelFieldVersion(relationClass, targetVersion);
//            return RelationUtil.fullTypeRegex(relationClass, relationType);
//        }
}
