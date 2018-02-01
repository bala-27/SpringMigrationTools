package org.tea.springify;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NewMain {

    public static void main(String[] args) throws FileNotFoundException {
        assert args.length == 1 : "give exactly on java file";
        assert args[0].endsWith(".java") : "only java files allowed, but filename is " + args[0];

        // creates an input stream for the file to be parsed
        FileInputStream in = new FileInputStream(args[0]);

        // parse the file
        CompilationUnit cu = JavaParser.parse(in);

        changeToConstructorInjection(cu);

        // prints the changed compilation unit
        System.out.println(cu.toString());
    }

    private static void changeToConstructorInjection(CompilationUnit cu) {
        boolean optionalImportNeeded = false;
        List<MethodDeclaration> nodesToRemove = new ArrayList<>();
        // Go through all the types in the file
        NodeList<TypeDeclaration<?>> types = cu.getTypes();
        for (TypeDeclaration<?> type : types) {
            // Set Type and Autowired for the constructor
            Node firstSetter = null;
            ConstructorDeclaration constructorDeclaration = new ConstructorDeclaration();
            constructorDeclaration.setName(type.getName());
            constructorDeclaration.addAnnotation("Autowired");

            NodeList<BodyDeclaration<?>> members = type.getMembers();
            for (BodyDeclaration<?> member : members) {
                if (member instanceof MethodDeclaration) {
                    MethodDeclaration method = (MethodDeclaration) member;
                    // only go for annotated methods
                    if (containsString(method.getAnnotations(), "@Autowired")) {
                        // special treatment for non required autowirings
                        boolean optionalNeeded = (containsString(method.getAnnotations(), "@Autowired(required = false)"));
                        // add Optional import if needed
                        optionalImportNeeded = optionalImportNeeded ? true : optionalNeeded;
                        buildConstructor(constructorDeclaration, method.getParameters(), method.getBody().orElse(new BlockStmt()).getStatements(), optionalNeeded);
                        // save first setter to replace it with the constructor
                        if (firstSetter == null) {
                            firstSetter = method;
                        } else{
                            nodesToRemove.add(method);
                        }
                    }
                }
            }
            // replace the first saved setter with the constructor
            firstSetter.replace(constructorDeclaration);
            // remove the no longer needed setter
            nodesToRemove.stream().forEach(Node::removeForced);
        }
        if(optionalImportNeeded){
            cu.addImport(Optional.class);
        }
    }

    // TODO this can be done more elegant using the elements of the AnnotationExpr
    private static boolean containsString(NodeList<AnnotationExpr> nodeList, String toBeContained) {
        if (nodeList == null) {
            return false;
        }
        for (AnnotationExpr node : nodeList) {
            if (node.toString().contains(toBeContained)) {
                return true;
            }
        }
        return false;
    }

    //TODO is treatment of Collections with required false correct?
    /**
     * Adds the contents of a method to an existing ConstructorDeclaration
     * @param constructorDeclaration
     * @param setterParameters should be of size 1 -> otherwise assertion will fail
     * @param setterBody
     * @param makeOptional when the previous setter Annotation had required = false make the value optional
     */
    private static void buildConstructor(ConstructorDeclaration constructorDeclaration, NodeList<Parameter> setterParameters, NodeList<Statement> setterBody, boolean makeOptional) {
        assert setterParameters.size() == 1;
        Parameter parameter = setterParameters.get(0);

        // add the Parameter
        String optionalVarName = null;
        if (makeOptional) {
            String typeOfNonRequired = parameter.getType().toString();
            optionalVarName = parameter.getNameAsString();
            constructorDeclaration.addParameter("Optional<" + typeOfNonRequired + ">", optionalVarName);
        } else {
            constructorDeclaration.addParameter(parameter);
        }
        // add the body to the constructor's body
        BlockStmt body = constructorDeclaration.getBody();
        for (Statement statement : setterBody) {
            // make parameter optional if makeOptional == true
            //TODO there are other Expressions which might be also needed -> e.g. VariableDeclarations fail here
            if (makeOptional && statement.isExpressionStmt()) {
                Expression expression = ((ExpressionStmt) statement).getExpression();
                if (expression.isAssignExpr()) {
                    Expression valueExpression = ((AssignExpr) expression).getValue();
                    if (valueExpression.isNameExpr()) {
                        String value = ((NameExpr) valueExpression).getNameAsString();
                        if (value.equals(optionalVarName)) {
                            value += ".orElse(null)";
                            ((NameExpr) valueExpression).setName(value);
                        }
                    }
                }
            }
            body.addStatement(statement);
        }
    }
}
