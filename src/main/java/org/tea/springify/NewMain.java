package org.tea.springify;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;


public class NewMain {

	public static void main(final String[] args) throws Exception {
		if (args.length != 1) {
			System.out.println("give exactly one parameter");
			System.exit(1);
		}
		if (!new File(args[0]).exists()) {
			System.out.println("the file " + args[0] + " does not exist");
		}
		final FileInputStream in = new FileInputStream(args[0]);
		final CompilationUnit cu = JavaParser.parse(in);

		changeToConstructorInjection(cu);
		System.out.println(cu.toString());
		// TODO write output -> maybe to same file
	}

	private static void changeToConstructorInjection(final CompilationUnit cu) throws Exception {
		boolean optionalImportNeeded = false;
		final List<MethodDeclaration> nodesToRemove = new ArrayList<>();
		// Go through all the types in the file
		final NodeList<TypeDeclaration<?>> types = cu.getTypes();
		for (final TypeDeclaration<?> type : types) {
			// Set Type and Autowired for the constructor
			Node firstSetter = null;
			final ConstructorDeclaration constructorDeclaration = new ConstructorDeclaration();
			constructorDeclaration.setName(type.getName());
			constructorDeclaration.addAnnotation("Autowired");

			final NodeList<BodyDeclaration<?>> members = type.getMembers();
			for (final BodyDeclaration<?> member : members) {
				if (member instanceof MethodDeclaration) {
					final MethodDeclaration method = (MethodDeclaration) member;
					// only go for annotated methods
					if (containsString(method.getAnnotations(), "@Autowired")) {
						// special treatment for non required autowirings
						final boolean optionalNeeded = (containsString(method.getAnnotations(), "@Autowired(required = false)"));
						// add Optional import if needed
						optionalImportNeeded = optionalImportNeeded ? true : optionalNeeded;
						buildConstructor(constructorDeclaration, method.getParameters(), method.getBody().orElse(new BlockStmt()).getStatements(),
								optionalNeeded);
						// save first setter to replace it with the constructor
						if (firstSetter == null) {
							firstSetter = method;
						} else {
							nodesToRemove.add(method);
						}
					}
				}
			}
			if (firstSetter != null) {
				// replace the first saved setter with the constructor
				firstSetter.replace(constructorDeclaration);
				// remove the no longer needed setter
				nodesToRemove.stream().forEach(Node::removeForced);
			}
		}
		if (optionalImportNeeded) {
			cu.addImport(Optional.class);
		}
	}

	// TODO this can be done more elegant using the elements of the AnnotationExpr -> replacing the string with an AnnotationExpr
	private static boolean containsString(final NodeList<AnnotationExpr> nodeList, final String toBeContained) {
		if (nodeList == null) {
			return false;
		}
		for (final AnnotationExpr node : nodeList) {
			if (node.toString().contains(toBeContained)) {
				return true;
			}
		}
		return false;
	}

	//TODO is another treatment of Collections with required false correct needed?
	/**
	 * Adds the contents of a method to an existing ConstructorDeclaration
	 * 
	 * @param constructorDeclaration
	 * @param setterParameters should be of size 1 -> otherwise assertion will fail
	 * @param setterBody
	 * @param makeOptional when the previous setter Annotation had required = false make the value optional
	 * @throws Exception
	 */
	private static void buildConstructor(final ConstructorDeclaration constructorDeclaration, final NodeList<Parameter> setterParameters,
			final NodeList<Statement> setterBody, final boolean makeOptional) throws Exception {
		assert setterParameters.size() == 1;
		final Parameter parameter = setterParameters.get(0);

		// add the Parameter
		String optionalVarName = null;
		if (makeOptional) {
			final String typeOfNonRequired = parameter.getType().toString();
			optionalVarName = parameter.getNameAsString();
			constructorDeclaration.addParameter("Optional<" + typeOfNonRequired + ">", optionalVarName);
		} else {
			constructorDeclaration.addParameter(parameter);
		}
		// add the body to the constructor's body
		final BlockStmt body = constructorDeclaration.getBody();
		boolean isOptionalUsed = false;
		for (final Statement statement : setterBody) {
			// make parameter optional if makeOptional == true
			//TODO there are other Expressions which might be also needed -> e.g. VariableDeclarations fail here
			if (makeOptional) {
				try {
					// only use NameExpr at the time, ignore everything else
					final NameExpr nameExpr = statement.asExpressionStmt().getExpression().asAssignExpr().getValue().asNameExpr();
					String value = nameExpr.getNameAsString();
					if (value.equals(optionalVarName)) {
						value += ".orElse(null)";
						nameExpr.setName(value);
						isOptionalUsed = true;
					}
				} catch (final IllegalStateException e) {
					// nothing todo, the Expression is simply not of intrest
				}
			}
			body.addStatement(statement);
		}
		if (makeOptional && !isOptionalUsed) {
			throw new Exception("Optional wasn't used");
		}
	}
}
