/*
 * Copyright 2009-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.eclipse.editor.highlighting;

import static org.eclipse.jdt.groovy.search.TypeLookupResult.TypeConfidence.UNKNOWN;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.MethodPointerExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.eclipse.editor.highlighting.HighlightedTypedPosition.HighlightKind;
import org.codehaus.jdt.groovy.model.GroovyCompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.groovy.core.util.GroovyUtils;
import org.eclipse.jdt.groovy.search.TypeLookupResult;
import org.eclipse.jdt.groovy.search.VariableScope;
import org.eclipse.jdt.internal.core.ImportDeclaration;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.internal.core.util.Util;
import org.eclipse.jface.text.Position;

/**
 * Finds deprecated/unknown references, GString expressions, regular expressions,
 * field/method/property references, static references, etc.
 */
public class SemanticHighlightingReferenceRequestor extends SemanticReferenceRequestor {

    private static final Position NO_POSITION;
    static {
        NO_POSITION = new Position(0);
        NO_POSITION.delete();
    }

    private char[] contents;
    private final GroovyCompilationUnit unit;
    private Position lastGString = NO_POSITION;
    private static final boolean DEBUG = false;

    /** Positions of interesting syntax elements within {@link #unit} in increasing lexical order. */
    protected final SortedSet<HighlightedTypedPosition> typedPosition = new TreeSet<HighlightedTypedPosition>(new Comparator<HighlightedTypedPosition>() {
        public int compare(HighlightedTypedPosition p1, HighlightedTypedPosition p2) {
            int result = p1.compareTo(p2);
            if (result == 0) {
                // order matching positions by highlighting style
                int x = p1.kind.ordinal(), y = p2.kind.ordinal();
                result = (x < y) ? -1 : ((x == y) ? 0 : 1);
            }
            return result;
        }
    });

    public SemanticHighlightingReferenceRequestor(GroovyCompilationUnit unit) {
        this.unit = unit;
    }

    // be sure to call this before referencing contents array
    private int unitLength() {
        if (contents == null) {
            contents = unit.getContents();
        }
        return contents.length;
    }

    public VisitStatus acceptASTNode(ASTNode node, TypeLookupResult result, IJavaElement enclosingElement) {

        // ignore statements or nodes with invalid source locations
        if (!(node instanceof AnnotatedNode) || node instanceof ImportNode || endOffset(node, result) < 1) {
            if (DEBUG) System.err.println("skipping: " + node);
            return VisitStatus.CONTINUE;
        }

        HighlightedTypedPosition pos = null;
        if (result.confidence == UNKNOWN && node.getEnd() > 0) {
            // GRECLIPSE-1327: check to see if this is a synthetic call() on a closure reference
            if (isRealASTNode(node)) {
                Position p = getPosition(node);
                typedPosition.add(new HighlightedTypedPosition(p, HighlightKind.UNKNOWN));
                // don't continue past an unknown reference
                return VisitStatus.CANCEL_BRANCH;
            }
        } else if (isDeprecated(result.declaration)) {
            pos = new HighlightedTypedPosition(getPosition(node), HighlightKind.DEPRECATED);

        } else if (result.declaration instanceof FieldNode || result.declaration instanceof PropertyNode) {
            pos = handleFieldOrProperty((AnnotatedNode) node, result.declaration);

        } else if (node instanceof MethodNode) {
            if (result.enclosingAnnotation == null) {
                pos = handleMethodDeclaration((MethodNode) node);
            } else {
                pos = handleAnnotationElement(result.enclosingAnnotation, (MethodNode) node);
            }

        } else if (node instanceof ConstructorCallExpression) {
            pos = handleMethodReference((ConstructorCallExpression) node);

        } else if (node instanceof MethodCallExpression) {
            pos = handleMethodReference((MethodCallExpression) node);

        } else if (node instanceof StaticMethodCallExpression) {
            pos = handleMethodReference((StaticMethodCallExpression) node);

        } else if (node instanceof MethodPointerExpression) {
            pos = handleMethodReference((MethodPointerExpression) node);

        } else if (node instanceof Parameter) {
            pos = handleVariableExpression((Parameter) node, result.scope);

        } else if (node instanceof VariableExpression) {
            if (result.declaration instanceof MethodNode) {
                pos = handleMethodReference((Expression) node, result, false);
            } else {
                pos = handleVariableExpression((VariableExpression) node, result.scope, enclosingElement);
            }
        } else if (node instanceof ConstantExpression) {
            if (result.declaration instanceof MethodNode) {
                pos = handleMethodReference((Expression) node, result, (enclosingElement instanceof ImportDeclaration));
            } else {
                pos = handleConstantExpression((ConstantExpression) node);
            }
        } else if (node instanceof GStringExpression) {
            pos = handleGStringExpression((GStringExpression) node);

        } else if (node instanceof MapEntryExpression) {
            pos = handleMapEntryExpression((MapEntryExpression) node);

        } else if (DEBUG) {
            String type = node.getClass().getSimpleName();
            if (!type.matches("ClassNode|(Class|Binary|ArgumentList|Closure(List)?|Declaration|Property|List|Map)Expression"))
                System.err.println("found: " + type);
        }

        if (pos != null && ((pos.getOffset() > 0 || pos.getLength() > 1) ||
                // expression nodes can still be valid and have an offset of 0 and
                // a length of 1, whereas for field/method nodes this is not allowed
                node instanceof Expression)) {
            typedPosition.add(pos);
        }

        return VisitStatus.CONTINUE;
    }

    // field and property declarations and references are handled the same
    private HighlightedTypedPosition handleFieldOrProperty(AnnotatedNode node, ASTNode decl) {
        HighlightKind kind;
        if (!isStatic(decl)) {
            kind = HighlightKind.FIELD;
        } else if (!isFinal(decl)) {
            kind = HighlightKind.STATIC_FIELD;
        } else /* static & final */ {
            kind = HighlightKind.STATIC_VALUE;
        }

        int offset, length;
        if (node == decl) {
            // declaration length includes the type and init
            offset = node.getNameStart();
            length = node.getNameEnd() - node.getNameStart() + 1;
        } else {
            offset = node.getStart();
            length = node.getLength();
        }

        return new HighlightedTypedPosition(offset, length, kind);
    }

    private HighlightedTypedPosition handleAnnotationElement(AnnotationNode anno, MethodNode elem) {
        try {
            int start = GroovyUtils.startOffset(anno), until = GroovyUtils.endOffset(anno);
            String source = unit.getSource().substring(start, until);

            // search for the element label in the source since no AST node exists for it
            Matcher m = Pattern.compile("\\b\\Q" + elem.getName() + "\\E\\b").matcher(source);
            if (m.find()) {
                return new HighlightedTypedPosition(start + m.start(), elem.getName().length(), HighlightKind.TAG_KEY);
            }
        } catch (Exception e) {
            Util.log(e);
        }
        return null;
    }

    private HighlightedTypedPosition handleMethodDeclaration(MethodNode node) {
        HighlightKind kind;
        if (node instanceof ConstructorNode) {
            kind = HighlightKind.CTOR;
        } else if (!isStatic(node)) {
            kind = HighlightKind.METHOD;
        } else {
            kind = HighlightKind.STATIC_METHOD;
        }

        int offset = node.getNameStart(),
            length = node.getNameEnd() - node.getNameStart() + 1;

        // special case: string literal method names
        if (length > node.getName().length()) return null;
        return new HighlightedTypedPosition(offset, length, kind);
    }

    private HighlightedTypedPosition handleMethodReference(MethodCallExpression expr) {
        HighlightKind kind = HighlightKind.METHOD_CALL;
        if (expr.getObjectExpression() instanceof ClassExpression) kind = HighlightKind.STATIC_CALL;

        int offset = expr.getMethod().getStart(),
            length = expr.getMethod().getLength();

        return new HighlightedTypedPosition(offset, length, kind);
    }

    private HighlightedTypedPosition handleMethodReference(ConstructorCallExpression expr) {
        if (expr.isSpecialCall()) {
            return null; // handled by GroovyTagScanner
        }

        int offset = expr.getNameStart(),
            length = expr.getNameEnd() - expr.getNameStart() + 1;

        return new HighlightedTypedPosition(offset, length, HighlightKind.CTOR_CALL);
    }

    private HighlightedTypedPosition handleMethodReference(StaticMethodCallExpression expr) {
        int offset = expr.getStart(),
            length = expr.getMethod().length();

        return new HighlightedTypedPosition(offset, length, HighlightKind.STATIC_CALL);
    }

    private HighlightedTypedPosition handleMethodReference(MethodPointerExpression expr) {
        HighlightKind kind = !(expr.getExpression() instanceof ClassExpression)
                ? HighlightKind.METHOD_CALL : HighlightKind.STATIC_CALL;

        int offset = expr.getMethodName().getStart(),
            length = expr.getMethodName().getLength();

        return new HighlightedTypedPosition(offset, length, kind);
    }

    private HighlightedTypedPosition handleMethodReference(Expression expr, TypeLookupResult result, boolean isStaticImport) {
        MethodNode meth = (MethodNode) result.declaration;

        HighlightKind kind = null;
        if (result.isGroovy) {
            kind = HighlightKind.GROOVY_CALL;
        } else if (isStaticImport) {
            kind = HighlightKind.STATIC_CALL;
        } else if (!expr.getText().equals(meth.getName())) {
            // property name did not match method name
            // there won't be a [Static]MethodCallExpression
            kind = !meth.isStatic() ? HighlightKind.METHOD_CALL : HighlightKind.STATIC_CALL;
        }

        if (kind != null) {
            return new HighlightedTypedPosition(expr.getStart(), expr.getLength(), kind);
        }
        return null;
    }

    private HighlightedTypedPosition handleVariableExpression(Parameter expr, VariableScope scope) {
        HighlightKind kind = HighlightKind.PARAMETER;
        if (isCatchParam(expr, scope) || isForLoopParam(expr, scope)) {
            kind = HighlightKind.VARIABLE; // treat block params as vars
        }
        return new HighlightedTypedPosition(expr.getNameStart(), expr.getNameEnd() - expr.getNameStart(), kind);
    }

    // could be local variable declaration, local variable reference, for-each parameter reference, or method parameter reference
    private HighlightedTypedPosition handleVariableExpression(VariableExpression expr, VariableScope scope, IJavaElement source) {
        boolean isParam = (expr.getAccessedVariable() instanceof Parameter &&
                !isForLoopParam(expr.getAccessedVariable(), scope)) &&
                !isCatchParam(expr.getAccessedVariable(), scope);
        boolean isIt = (isParam && "it".equals(expr.getName()) &&
                (((Parameter) expr.getAccessedVariable()).getLineNumber() <= 0));
        boolean isSuperOrThis = "super".equals(expr.getName()) || "this".equals(expr.getName());

        // free vars and loop vars are okay as long as they are not reserved words (this, super); params must refer to "real" declarations
        if (!isSuperOrThis && (!isParam || isIt || (((Parameter) expr.getAccessedVariable()).getLineNumber() > 0) || source instanceof SourceType)) {
            HighlightKind kind = isParam ? (isIt ? HighlightKind.GROOVY_CALL : HighlightKind.PARAMETER) : HighlightKind.VARIABLE;
            return new HighlightedTypedPosition(expr.getStart(), expr.getLength(), kind);
        }
        return null;
    }

    private HighlightedTypedPosition handleConstantExpression(ConstantExpression expr) {
        int offset = expr.getStart(),
            length = expr.getLength();

        HighlightedTypedPosition pos = null;
        if (!lastGString.includes(offset)) {
            if (isNumber(expr.getType())) {
                pos = new HighlightedTypedPosition(offset, length, HighlightKind.NUMBER);
            } else if (expr.getEnd() <= unitLength()) {
                // check for /.../ or $/.../$ form of string literal (usually a regex literal)
                boolean slashy = contents[offset] == '/' && contents[expr.getEnd() - 1] == '/';
                boolean dollar = !slashy && contents[offset] == '$' && contents[offset + 1] == '/' &&
                        contents[expr.getEnd() - 2] == '/' && contents[expr.getEnd() - 1] == '$';
                if (slashy || dollar) {
                    pos = new HighlightedTypedPosition(offset, length, HighlightKind.REGEXP);
                }
            }
        }
        return pos;
    }

    private HighlightedTypedPosition handleGStringExpression(GStringExpression expr) {
        int offset = expr.getStart(),
            length = expr.getLength();

        // save to help deal with forthcoming ConstantExpression nodes
        lastGString = new Position(offset, length);

        // check for slashy form of GString
        if (expr.getEnd() <= unitLength()) {
            String source = String.valueOf(contents, offset, length);
            if ((source.startsWith("/") && source.endsWith("/")) ||
                    (source.startsWith("$/") && source.endsWith("/$"))) {
                return new HighlightedTypedPosition(lastGString, HighlightKind.REGEXP);
            }
        }
        return null;
    }

    private HighlightedTypedPosition handleMapEntryExpression(MapEntryExpression expr) {
        Expression key = expr.getKeyExpression();
        if (key instanceof ConstantExpression) {
            unitLength(); // ensure loaded
            char c = contents[key.getStart()];
            if (c != '\'' && c != '"' && c != '/') {
                return new HighlightedTypedPosition(key.getStart(), key.getLength(), HighlightKind.MAP_KEY);
            }
        }
        return null;
    }

    private int endOffset(ASTNode node, TypeLookupResult result) {
        int offset = node.getEnd();
        if (result.enclosingAnnotation != null) {
            offset = result.enclosingAnnotation.getEnd();
            // TODO: Probably could be more accurate, but doesn't need to be at the moment...
        }
        return offset;
    }

    /**
     * An AST node is "real" if it is an expression and the
     * text of the expression matches the actual text in the file
     */
    private boolean isRealASTNode(ASTNode node) {
        String text = node.getText();
        if (text.length() != node.getLength()) {
            return false;
        }
        int contentsLength = unitLength();
        char[] textArr = text.toCharArray();
        for (int i = 0, j = node.getStart(); i < textArr.length && j < contentsLength; i++, j++) {
            if (textArr[i] != contents[j]) {
                return false;
            }
        }
        return true;
    }
}
