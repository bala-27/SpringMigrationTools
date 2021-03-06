package org.tea.springify;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
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


		// save the constructor + setters as strings in as list
		// also save comments usw...

		final List<List<String>> results = changeToConstructorInjection(cu);
		
		try {
			in.close();
		} catch (final IOException e) {
		}
		
		final List<String> file = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);

		for (final List<String> result: results) {

			assert result != null;
			assert result.size() != 1;
			if (result.size() == 0) {
				continue;
			}
			replaceFirstSetterWithConstructor(result.get(0), result.get(1), file);


			// do the magic
		}
		
		// build regex for the setters
		// make Constructor format look like inside eclipse -> get indents of first setter
		// replace first setter by constructor
		// replace rest of the setters by empty strings
		// remove dispensable newlines
		// check for imports 
	}

	private static List<List<String>> changeToConstructorInjection(final CompilationUnit cu) throws Exception {
		boolean optionalImportNeeded = false;
		final List<List<String>> results = new ArrayList<>();

		// Go through all the types in the file
		final NodeList<TypeDeclaration<?>> types = cu.getTypes();
		for (final TypeDeclaration<?> type : types) {

			// Set Type and Autowired for the constructor
			final ConstructorDeclaration constructorDeclaration = new ConstructorDeclaration();
			constructorDeclaration.setName(type.getName());
			constructorDeclaration.addAnnotation("Autowired");

			final List<String> result = new ArrayList<>();

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

						result.add(method.toString());
					}
				}
			}

			if (result.size() > 0) {
				result.add(0, constructorDeclaration.toString());
				results.add(result);
			}
		}

		if (optionalImportNeeded) {
			// TODO this information is still relevant and must be added where it is required
			cu.addImport(Optional.class);
			// maybe the string can be added to the last field of the array and the last field checked
		}
		return results;
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

	//TODO is another treatment of Collections with required=false needed?
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
					// nothing todo, the Expression is simply not of interest
				}
			}
			body.addStatement(statement);
		}
		if (makeOptional && !isOptionalUsed) {
			throw new Exception("Optional wasn't used");
		}
	}

	private static void replaceFirstSetterWithConstructor(final String constructor, final String firstSetter, final String file) {

	}

	private static String toRegex(final String declaration) {
		final String regex = null;
		// correct replacement of blanks problem {{}}
		return regex;
	}

	private static void replaceSetterByEmpty(final String setter, final String file) {
		final String setterRegex = toRegex(setter);

	}
}
