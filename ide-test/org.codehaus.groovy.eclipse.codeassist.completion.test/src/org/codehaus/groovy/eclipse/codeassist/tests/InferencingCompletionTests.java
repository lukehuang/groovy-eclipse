/*******************************************************************************
 * Copyright (c) 2009 SpringSource and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Andrew Eisenberg - initial API and implementation
 *******************************************************************************/

package org.codehaus.groovy.eclipse.codeassist.tests;

import org.codehaus.groovy.eclipse.codeassist.completion.jdt.GeneralGroovyCompletionProcessor;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

/**
 * @author Andrew Eisenberg
 * @created Jul 23, 2009
 *
 */
public class InferencingCompletionTests extends CompletionTestCase {

    
    public InferencingCompletionTests() {
        super("Inferencing Completion Test Cases");
    }

    // FIXADE (M1) re-enable this test for when the problem with VariableScopeVisitor is fixed
    private static final String CONTENTS = "class TransformerTest extends GroovyTestCase {\nvoid testTransformer() {\ndef s = \"string\"\ns.st\n}}";
    public void testInferenceOfLocalStringInMethod() throws Exception {
        IPath projectPath = createGenericProject();
        IPath pack = projectPath.append("src");
        IPath pathToJavaClass = env.addGroovyClass(pack, "TransformerTest", CONTENTS);
        incrementalBuild();
        ICompilationUnit unit = getCompilationUnit(pathToJavaClass);
        unit.becomeWorkingCopy(null);
        ICompletionProposal[] proposals = performContentAssist(unit, CONTENTS.indexOf("s.st") + "s.ts".length(), GeneralGroovyCompletionProcessor.class);
        proposalExists(proposals, "startsWith", 2);
    }

    private static final String CONTENTS_SCRIPT = 
        "def s = \"string\"\n" +
        "s.st\n" +
        "s.substring(0).sub\n" +
        "class AClass {\n " +
        "  def g() {\n" +
        "    def t" +
        "    t = \"\"" +
        "    t.st" +
        "  }" +
        "}";
    public void testInferenceOfLocalString() throws Exception {
        IPath projectPath = createGenericProject();
        IPath pack = projectPath.append("src");
        IPath pathToJavaClass = env.addGroovyClass(pack, "TransformerTest", CONTENTS_SCRIPT);
        incrementalBuild();
        ICompilationUnit unit = getCompilationUnit(pathToJavaClass);
        unit.becomeWorkingCopy(null);
        ICompletionProposal[] proposals = performContentAssist(unit, CONTENTS_SCRIPT.indexOf("s.st") + "s.ts".length(), GeneralGroovyCompletionProcessor.class);
        proposalExists(proposals, "startsWith", 2);
    }
    public void testInferenceOfLocalString2() throws Exception {
        IPath projectPath = createGenericProject();
        IPath pack = projectPath.append("src");
        IPath pathToJavaClass = env.addGroovyClass(pack, "TransformerTest", CONTENTS_SCRIPT);
        incrementalBuild();
        ICompilationUnit unit = getCompilationUnit(pathToJavaClass);
        unit.becomeWorkingCopy(null);
        ICompletionProposal[] proposals = performContentAssist(unit, CONTENTS_SCRIPT.indexOf("0).sub") + "0).sub".length(), GeneralGroovyCompletionProcessor.class);
        proposalExists(proposals, "substring", 2);
    }
    
    public void testInferenceOfStringInClass() throws Exception {
        IPath projectPath = createGenericProject();
        IPath pack = projectPath.append("src");
        IPath pathToJavaClass = env.addGroovyClass(pack, "TransformerTest", CONTENTS_SCRIPT);
        incrementalBuild();
        ICompilationUnit unit = getCompilationUnit(pathToJavaClass);
        unit.becomeWorkingCopy(null);
        ICompletionProposal[] proposals = performContentAssist(unit, CONTENTS_SCRIPT.indexOf("t.st") + "t.ts".length(), GeneralGroovyCompletionProcessor.class);
        proposalExists(proposals, "startsWith", 2);
    }
}
