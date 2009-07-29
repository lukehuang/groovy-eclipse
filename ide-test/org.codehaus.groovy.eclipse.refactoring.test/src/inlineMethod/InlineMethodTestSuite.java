/* 
 * Copyright (C) 2007, 2009 Martin Kempf, Reto Kleeb, Michael Klenk
 *
 * IFS Institute for Software, HSR Rapperswil, Switzerland
 * http://ifs.hsr.ch/
 *
 */
package inlineMethod;

import java.io.File;
import java.util.List;

import junit.framework.TestSuite;
import tests.BaseTestSuite;

public class InlineMethodTestSuite extends BaseTestSuite {

	public static TestSuite suite() {
		TestSuite ts = new TestSuite("Inline Method Suite");
		List<File> files = getFileList("/inlineMethodFiles","InlineMethod_Test_");
		for (File file : files) {		
			ts.addTest(new InlineMethodTestCase(file.getName(),file));
		}
		return ts;
	}
}
