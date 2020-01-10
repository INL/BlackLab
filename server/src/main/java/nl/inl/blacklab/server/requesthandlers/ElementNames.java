package nl.inl.blacklab.server.requesthandlers;

/**
 * Specifies what element names to use for various elements.
 * 
 * Needed because BlackLab's terminology for annotated fields changed
 * (formerly complex field / property), and we want the option of
 * supporting the old version as well.
 */
public class ElementNames {
    
    public static String isAnnotatedField;
    
    public static String annotatedField;
    
    public static String annotatedFields;
    
    public static String annotations;
    
    public static String annotation;

    public static String mainProperty;
    
    public static String subannotation;
    
    public static String subannotations;
    
    static {
        setUseOldElementNames(false);
    }
    
    public static void setUseOldElementNames(boolean b) {
        if (b) {
            isAnnotatedField = "isComplexField";
            annotatedField = "complexField";
            annotatedFields = "complexFields";
            annotations = "properties";
            annotation = "property";
            mainProperty = "mainProperty";
            subannotation = "subproperty";
            subannotations = "subproperties";
        } else {
            isAnnotatedField = "isAnnotatedField";
            annotatedField = "annotatedField";
            annotatedFields = "annotatedFields";
            annotations = "annotations";
            annotation = "annotation";
            mainProperty = "mainAnnotation";
            subannotation = "subannotation";
            subannotations = "subannotations";
        }
    }

    public static boolean isUseOldElementNames() {
        return annotatedField.equals("complexField");
    }
    
}
